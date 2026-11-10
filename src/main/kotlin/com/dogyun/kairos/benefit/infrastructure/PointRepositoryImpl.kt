package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.MemberPoint
import com.dogyun.kairos.benefit.domain.PointLot
import com.dogyun.kairos.benefit.domain.PointLotUsage
import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointRepository
import com.dogyun.kairos.benefit.domain.PointTransaction
import com.dogyun.kairos.benefit.domain.PointTransactionType
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PointRepositoryImpl(
    private val memberPoints: MemberPointJpaRepository,
    private val lots: PointLotJpaRepository,
    private val transactions: PointTransactionJpaRepository,
    private val usages: PointLotUsageJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
) : PointRepository {

    override fun lockOrCreate(memberId: Long): MemberPoint {
        // 없는 행에 FOR UPDATE 를 먼저 걸면 REPEATABLE READ 의 갭 락끼리 INSERT 에서 교착한다. 그래서 잠금 없는 확인 → INSERT ON DUPLICATE → FOR UPDATE 순서다.
        // INSERT 의 UNIQUE 충돌은 JPA 로 하면 같은 트랜잭션(영속성 컨텍스트)을 더럽히므로 JdbcTemplate 으로 처리한다.
        // INSERT IGNORE 는 중복 행에 공유 락을 잡아서, 대기하던 트랜잭션들이 FOR UPDATE 로 올릴 때 서로 교착한다. ON DUPLICATE KEY UPDATE 는 처음부터 배타 락이다.
        val exists = jdbcTemplate.queryForList("select 1 from member_point where member_id = ?", memberId).isNotEmpty()
        if (!exists) {
            jdbcTemplate.update(
                "insert into member_point (member_id, balance, created_at, created_by, modified_at, modified_by, status) " +
                    "values (?, 0, now(6), 0, now(6), 0, 'ACTIVE') on duplicate key update balance = balance",
                memberId,
            )
        }
        return toDomain(checkNotNull(memberPoints.findLockedByMemberId(memberId)) { "member_point 를 만들지 못했다: memberId=$memberId" })
    }

    override fun save(point: MemberPoint) {
        // 이미 잠근 행이다. 일반 조회는 REPEATABLE READ 스냅샷 때문에 방금 만든 행을 못 볼 수 있어 잠금 조회로 다시 읽는다.
        val entity = checkNotNull(memberPoints.findLockedByMemberId(point.memberId)) { "member_point 가 없다: memberId=${point.memberId}" }
        entity.changeBalance(point.balance)
        memberPoints.saveAndFlush(entity)
    }

    override fun findByMemberId(memberId: Long): MemberPoint? = memberPoints.findByMemberId(memberId)?.let(::toDomain)

    override fun findConsumableLots(memberId: Long, now: LocalDateTime): List<PointLot> =
        lots.findConsumable(memberId, now).map(::toDomain)

    override fun findLotsByIds(ids: Collection<Long>): List<PointLot> =
        if (ids.isEmpty()) emptyList() else lots.findAllById(ids).map(::toDomain)

    override fun saveLot(lot: PointLot): PointLot {
        val entity = lot.id?.let { id ->
            lots.findById(id).orElseThrow().also { it.changeRemaining(lot.remainingAmount) }
        } ?: PointLotEntity(lot.memberId, lot.amount, lot.remainingAmount, lot.sourceType, lot.sourceId, lot.expiresAt)
        return toDomain(lots.saveAndFlush(entity))
    }

    override fun saveLots(lots: Collection<PointLot>) {
        lots.forEach { saveLot(it) }
    }

    override fun findTransaction(type: PointTransactionType, refType: PointRefType, refId: Long): PointTransaction? =
        transactions.findByTransactionTypeAndRefTypeAndRefId(type, refType, refId)?.let { toDomain(it, withUsages = true) }

    override fun saveTransaction(tx: PointTransaction): PointTransaction {
        val saved = transactions.saveAndFlush(
            PointTransactionEntity(tx.memberId, tx.type, tx.amount, tx.balanceAfter, tx.refType, tx.refId),
        )
        tx.usages.forEach { usages.save(PointLotUsageEntity(saved.id, it.lotId, it.amount, it.revertedAmount)) }
        usages.flush()
        return toDomain(saved, withUsages = true)
    }

    override fun saveUsages(usages: Collection<PointLotUsage>) {
        usages.forEach { u ->
            val entity = this.usages.findById(requireNotNull(u.id)).orElseThrow()
            entity.changeReverted(u.revertedAmount)
            this.usages.save(entity)
        }
        this.usages.flush()
    }

    override fun findPageTransactions(memberId: Long, page: Int, size: Int): PageResult<PointTransaction> {
        val result = transactions.findByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size))
        return PageResult.of(result.content.map { toDomain(it, withUsages = false) }, page, size, result.totalElements)
    }

    override fun sumExpiringBetween(memberId: Long, from: LocalDateTime, to: LocalDateTime): Long =
        lots.sumExpiringBetween(memberId, from, to)

    override fun findExpiredLots(now: LocalDateTime, afterId: Long, limit: Int): List<Pair<Long, Long>> =
        lots.findExpired(now, afterId, PageRequest.of(0, limit)).map { it.memberId to it.id }

    override fun findLotForUpdate(lotId: Long): PointLot? = lots.findLockedById(lotId)?.let(::toDomain)

    private fun toDomain(e: MemberPointEntity) = MemberPoint(e.id, e.memberId, e.balance)

    private fun toDomain(e: PointLotEntity) =
        PointLot(e.id, e.memberId, e.amount, e.remainingAmount, e.sourceType, e.sourceId, e.expiresAt)

    private fun toDomain(e: PointTransactionEntity, withUsages: Boolean) = PointTransaction(
        e.id, e.memberId, e.transactionType, e.amount, e.balanceAfter, e.refType, e.refId,
        if (withUsages) usages.findByTransactionIdOrderById(e.id).map { PointLotUsage(it.id, it.lotId, it.amount, it.revertedAmount) } else emptyList(),
        e.createdAt,
    )
}
