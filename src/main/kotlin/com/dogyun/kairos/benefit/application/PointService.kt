package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.PointLot
import com.dogyun.kairos.benefit.domain.PointLotUsage
import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointRepository
import com.dogyun.kairos.benefit.domain.PointSourceType
import com.dogyun.kairos.benefit.domain.PointTransaction
import com.dogyun.kairos.benefit.domain.PointTransactionType
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 포인트 적립·사용·되돌림·만료. 모든 메서드는 첫 줄에서 회원 포인트 행을 잠가(ADR 0004: `member_point → … → point_lot`)
 * 같은 회원의 변경을 직렬화한다. 원장 UNIQUE(transaction_type, ref_type, ref_id)가 멱등 키다.
 */
@Service
@Transactional
class PointService(
    private val pointRepository: PointRepository,
    private val properties: BenefitProperties,
    private val clock: Clock,
) {

    /** 새로 적립했으면 true. [amount] 가 0 이하이거나 같은 (type, refType, refId) 가 이미 있으면 false. */
    fun earn(
        memberId: Long,
        amount: Long,
        type: PointTransactionType,
        source: PointSourceType,
        refType: PointRefType,
        refId: Long,
        expiresAt: LocalDateTime,
    ): Boolean {
        val point = pointRepository.lockOrCreate(memberId)
        if (amount <= 0) return false
        point.add(amount)
        // 원장이 멱등 키다. 먼저 넣어 보고 중복이면 아무 것도 바꾸지 않는다.
        pointRepository.saveTransactionIfAbsent(PointTransaction(null, memberId, type, amount, point.balance, refType, refId)) ?: return false
        pointRepository.saveLot(PointLot(null, memberId, amount, amount, source, refId, expiresAt))
        pointRepository.save(point)
        return true
    }

    /** 구매 확정 적립: `floor(paidAmount × earnRateBp / 10000)`. 새로 적립한 양(없으면 0). */
    fun earnForPurchase(memberId: Long, sellerOrderId: Long, paidAmount: Long): Long {
        val amount = paidAmount * properties.earnRateBp / 10_000
        val expiresAt = LocalDateTime.now(clock).plus(properties.earnExpiry)
        val earned = earn(
            memberId, amount, PointTransactionType.EARN_PURCHASE, PointSourceType.PURCHASE,
            PointRefType.PURCHASE_CONFIRM, sellerOrderId, expiresAt,
        )
        return if (earned) amount else 0L
    }

    /** 후기 보상 적립(글 100, 사진 500). 새로 적립한 양(없으면 0). */
    fun earnForReview(memberId: Long, reviewId: Long, hasPhoto: Boolean): Long {
        val amount = if (hasPhoto) properties.reviewRewardPhoto else properties.reviewRewardText
        val expiresAt = LocalDateTime.now(clock).plus(properties.earnExpiry)
        val earned = earn(
            memberId, amount, PointTransactionType.EARN_REVIEW, PointSourceType.REVIEW,
            PointRefType.REVIEW, reviewId, expiresAt,
        )
        return if (earned) amount else 0L
    }

    /** 만료가 가까운 로트부터 [amount] 를 쓴다. 같은 주문에 두 번 쓰면 `IllegalStateException`. */
    fun use(memberId: Long, orderId: Long, amount: Long) {
        val point = pointRepository.lockOrCreate(memberId)
        if (amount == 0L) return
        require(amount > 0) { "사용액은 0 이상이어야 한다." }
        if (point.balance < amount) throw DomainException(ErrorCode.POINT_INSUFFICIENT)

        var left = amount
        val touched = mutableListOf<PointLot>()
        val usages = mutableListOf<PointLotUsage>()
        for (lot in pointRepository.findConsumableLots(memberId, LocalDateTime.now(clock))) {
            if (left == 0L) break
            val taken = lot.consume(left)
            if (taken == 0L) continue
            left -= taken
            touched += lot
            usages += PointLotUsage(null, requireNotNull(lot.id), taken, 0)
        }
        // 잔액은 충분한데 사용 가능한 로트가 모자란 경우(만료 작업 전의 만료 로트)도 부족으로 본다.
        if (left > 0) throw DomainException(ErrorCode.POINT_INSUFFICIENT)

        point.subtract(amount)
        checkNotNull(
            pointRepository.saveTransactionIfAbsent(
                PointTransaction(null, memberId, PointTransactionType.USE, -amount, point.balance, PointRefType.ORDER, orderId, usages),
            ),
        ) { "이미 포인트를 사용한 주문이다: orderId=$orderId" }
        pointRepository.saveLots(touched)
        pointRepository.save(point)
    }

    /**
     * 주문의 사용 내역을 id 역순으로 [amount] 만큼 되돌린다. 만료 전 로트는 그 로트로, 만료된 로트 몫은 새 환급 로트 하나로 돌려준다.
     * 이미 같은 (refType, refId) 로 되돌렸거나 [amount] 가 0 이면 false.
     */
    fun refund(memberId: Long, orderId: Long, refType: PointRefType, refId: Long, amount: Long): Boolean {
        val point = pointRepository.lockOrCreate(memberId)
        if (amount == 0L) return false
        require(amount > 0) { "되돌림 금액은 0 이상이어야 한다." }
        val use = checkNotNull(pointRepository.findTransaction(PointTransactionType.USE, PointRefType.ORDER, orderId)) {
            "포인트 사용 내역이 없는 주문이다: orderId=$orderId"
        }
        point.add(amount)
        // 같은 되돌림을 이미 했다면(원장 UNIQUE 충돌) 계산 전에 false. 계산이 예외로 끝나면 트랜잭션째 롤백되므로 원장도 남지 않는다.
        pointRepository.saveTransactionIfAbsent(
            PointTransaction(null, memberId, PointTransactionType.REFUND, amount, point.balance, refType, refId),
        ) ?: return false
        val now = LocalDateTime.now(clock)
        val lots = pointRepository.findLotsByIds(use.usages.map { it.lotId }.toSet()).associateBy { it.id }

        var left = amount
        var toNewLot = 0L
        val updatedUsages = mutableListOf<PointLotUsage>()
        val restoredLots = mutableListOf<PointLot>()
        for (usage in use.usages.sortedByDescending { it.id }) {
            if (left == 0L) break
            val take = minOf(left, usage.revertible)
            if (take == 0L) continue
            val lot = checkNotNull(lots[usage.lotId]) { "사용 로트가 없다: lotId=${usage.lotId}" }
            if (lot.isExpired(now)) {
                toNewLot += take
            } else {
                lot.restore(take)
                restoredLots += lot
            }
            updatedUsages += usage.reverted(take)
            left -= take
        }
        check(left == 0L) { "되돌릴 양이 사용 내역의 남은 양보다 크다: orderId=$orderId, amount=$amount" }

        pointRepository.saveLots(restoredLots)
        if (toNewLot > 0) {
            val expiresAt = now.plus(properties.refundLotExpiry)
            pointRepository.saveLot(PointLot(null, memberId, toNewLot, toNewLot, PointSourceType.REFUND, refId, expiresAt))
        }
        pointRepository.saveUsages(updatedUsages)
        pointRepository.save(point)
        return true
    }

    /** 만료된 로트의 남은 양을 없앤다. 회원 락을 먼저 잡은 뒤 로트를 잠근다(`use`/`refund` 와 같은 순서). */
    fun expireLot(memberId: Long, lotId: Long): Boolean {
        val point = pointRepository.lockOrCreate(memberId)
        val lot = pointRepository.findLotForUpdate(lotId) ?: return false
        if (lot.memberId != memberId) return false
        if (!lot.isExpired(LocalDateTime.now(clock)) || lot.remainingAmount == 0L) return false

        val remaining = lot.remainingAmount
        point.subtract(remaining)
        pointRepository.saveTransactionIfAbsent(
            PointTransaction(null, memberId, PointTransactionType.EXPIRE, -remaining, point.balance, PointRefType.POINT_LOT, lotId),
        ) ?: return false
        lot.expire()
        pointRepository.saveLot(lot)
        pointRepository.save(point)
        return true
    }
}
