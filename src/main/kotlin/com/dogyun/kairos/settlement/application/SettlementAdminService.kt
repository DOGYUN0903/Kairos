package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import com.dogyun.kairos.settlement.domain.SettlementRepository
import com.dogyun.kairos.settlement.domain.SettlementStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 관리자 정산 조회·확정·지급 처리. 상태 전이는 정산 행을 `FOR UPDATE` 로 잠근 뒤 현재 상태로 정한다
 * (GENERATED → CONFIRMED → PAID, 건너뛰기 없음, 되풀이는 변화 없이 200).
 */
@Service
@Transactional
class SettlementAdminService(
    private val settlementRepository: SettlementRepository,
    private val adjustmentRepository: SettlementAdjustmentRepository,
    private val sellerReader: SettlementSellerReader,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun list(sellerId: Long?, status: SettlementStatus?, page: Int, size: Int): PageResult<SettlementSummary> {
        val result = settlementRepository.findPageForAdmin(sellerId, status, page, size)
        val sellers = sellerReader.findSellers(result.content.map { it.sellerId }.toSet())
        return PageResult(
            result.content.map { toSummary(it, sellers[it.sellerId]) },
            result.page, result.size, result.totalElements, result.totalPages, result.hasNext,
        )
    }

    @Transactional(readOnly = true)
    fun get(settlementId: Long): SettlementDetail {
        val s = settlementRepository.findById(settlementId) ?: throw DomainException(ErrorCode.SETTLEMENT_NOT_FOUND)
        return detail(s)
    }

    fun confirm(settlementId: Long): SettlementDetail {
        val s = lock(settlementId)
        s.confirm(LocalDateTime.now(clock))
        return detail(settlementRepository.save(s))
    }

    fun markPaid(settlementId: Long, memo: String?): SettlementDetail {
        val s = lock(settlementId)
        s.markPaid(LocalDateTime.now(clock), memo)
        return detail(settlementRepository.save(s))
    }

    private fun lock(id: Long): Settlement = settlementRepository.findByIdForUpdate(id) ?: throw DomainException(ErrorCode.SETTLEMENT_NOT_FOUND)

    private fun detail(s: Settlement): SettlementDetail {
        val id = requireNotNull(s.id)
        val seller = sellerReader.findSellers(listOf(s.sellerId))[s.sellerId]
        return SettlementDetail(
            settlementId = id, sellerId = s.sellerId, sellerName = seller?.companyName.orEmpty(), sellerStatus = seller?.sellerStatus.orEmpty(),
            periodStart = s.period.start, periodEnd = s.period.end, payoutDate = s.period.payoutDate, status = s.status,
            totalAmount = s.totalAmount, payableAmount = s.payableAmount, lineCount = s.lineCount,
            salesAmount = s.salesAmount, sellerCouponDiscount = s.sellerCouponDiscount, commissionAmount = s.commissionAmount,
            shippingFeeOwed = s.shippingFeeOwed, returnFeeBorne = s.returnFeeBorne, adjustmentAmount = s.adjustmentAmount,
            confirmedAt = s.confirmedAt, paidAt = s.paidAt, paidMemo = s.paidMemo,
            lines = s.lines.map {
                val c = it.calc
                SettlementLineResult(
                    requireNotNull(it.id), it.sellerOrderId, it.orderId, it.orderNo, it.confirmedAt, c.salesAmount, c.sellerCouponDiscount,
                    c.commissionBase, c.commissionRateBp, c.commissionAmount, c.shippingFeeOwed, c.returnFeeBorne, c.lineAmount,
                )
            },
            adjustments = adjustmentRepository.findBySettlementId(id).map(::toResult),
        )
    }

    private fun toSummary(s: Settlement, seller: SettlementSellerInfo?) = SettlementSummary(
        requireNotNull(s.id), s.sellerId, seller?.companyName.orEmpty(), seller?.sellerStatus.orEmpty(),
        s.period.start, s.period.end, s.period.payoutDate, s.status, s.totalAmount, s.payableAmount, s.lineCount,
    )
}
