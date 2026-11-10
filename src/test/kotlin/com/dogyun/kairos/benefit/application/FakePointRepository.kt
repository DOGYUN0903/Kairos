package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.MemberPoint
import com.dogyun.kairos.benefit.domain.PointLot
import com.dogyun.kairos.benefit.domain.PointLotUsage
import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointRepository
import com.dogyun.kairos.benefit.domain.PointTransaction
import com.dogyun.kairos.benefit.domain.PointTransactionType
import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

/** 저장된 값만 보관하는 인메모리 구현. 서비스가 저장하지 않은 변경은 반영되지 않도록 저장 시점에 복사한다. */
class FakePointRepository : PointRepository {
    val points = linkedMapOf<Long, MemberPoint>()
    val lots = linkedMapOf<Long, PointLot>()
    val transactions = linkedMapOf<Long, PointTransaction>()
    private val usagesByTx = linkedMapOf<Long, MutableList<PointLotUsage>>()
    private var nextPointId = 1L
    private var nextLotId = 1L
    private var nextTxId = 1L
    private var nextUsageId = 1L

    override fun lockOrCreate(memberId: Long): MemberPoint {
        val stored = points.getOrPut(memberId) { MemberPoint(nextPointId++, memberId, 0) }
        return MemberPoint(stored.id, memberId, stored.balance)
    }

    override fun save(point: MemberPoint) {
        points[point.memberId] = MemberPoint(point.id, point.memberId, point.balance)
    }

    override fun findByMemberId(memberId: Long): MemberPoint? = points[memberId]?.let { MemberPoint(it.id, it.memberId, it.balance) }

    override fun findConsumableLots(memberId: Long, now: LocalDateTime): List<PointLot> =
        lots.values.filter { it.memberId == memberId && it.remainingAmount > 0 && it.expiresAt.isAfter(now) }
            .sortedWith(compareBy({ it.expiresAt }, { it.id })).map(::copy)

    override fun findLotsByIds(ids: Collection<Long>): List<PointLot> = ids.mapNotNull { lots[it] }.map(::copy)

    override fun saveLot(lot: PointLot): PointLot {
        val id = lot.id ?: nextLotId++
        val saved = PointLot(id, lot.memberId, lot.amount, lot.remainingAmount, lot.sourceType, lot.sourceId, lot.expiresAt)
        lots[id] = saved
        return copy(saved)
    }

    override fun saveLots(lots: Collection<PointLot>) {
        lots.forEach { saveLot(it) }
    }

    override fun findTransaction(type: PointTransactionType, refType: PointRefType, refId: Long): PointTransaction? =
        transactions.values.firstOrNull { it.type == type && it.refType == refType && it.refId == refId }?.let(::withUsages)

    override fun saveTransaction(tx: PointTransaction): PointTransaction {
        check(findTransaction(tx.type, tx.refType, tx.refId) == null) { "uk_point_transaction_ref" }
        val id = nextTxId++
        val savedUsages = tx.usages.map { it.copy(id = nextUsageId++) }.toMutableList()
        usagesByTx[id] = savedUsages
        val saved = PointTransaction(id, tx.memberId, tx.type, tx.amount, tx.balanceAfter, tx.refType, tx.refId, emptyList(), LocalDateTime.now())
        transactions[id] = saved
        return withUsages(saved)
    }

    override fun saveUsages(usages: Collection<PointLotUsage>) {
        usages.forEach { updated ->
            usagesByTx.values.forEach { list ->
                val i = list.indexOfFirst { it.id == updated.id }
                if (i >= 0) list[i] = updated
            }
        }
    }

    override fun findPageTransactions(memberId: Long, page: Int, size: Int): PageResult<PointTransaction> {
        val all = transactions.values.filter { it.memberId == memberId }.sortedByDescending { it.id }
        return PageResult.of(all.drop(page * size).take(size).map(::withUsages), page, size, all.size.toLong())
    }

    override fun sumExpiringBetween(memberId: Long, from: LocalDateTime, to: LocalDateTime): Long =
        lots.values.filter { it.memberId == memberId && it.expiresAt.isAfter(from) && !it.expiresAt.isAfter(to) }.sumOf { it.remainingAmount }

    override fun findExpiredLots(now: LocalDateTime, afterId: Long, limit: Int): List<Pair<Long, Long>> =
        lots.values.filter { it.remainingAmount > 0 && !it.expiresAt.isAfter(now) && it.id!! > afterId }
            .sortedBy { it.id }.take(limit).map { it.memberId to it.id!! }

    override fun findLotForUpdate(lotId: Long): PointLot? = lots[lotId]?.let(::copy)

    fun lot(id: Long): PointLot = requireNotNull(lots[id])
    fun balance(memberId: Long): Long = points[memberId]?.balance ?: 0L
    fun remainingSum(memberId: Long): Long = lots.values.filter { it.memberId == memberId }.sumOf { it.remainingAmount }

    private fun copy(l: PointLot) = PointLot(l.id, l.memberId, l.amount, l.remainingAmount, l.sourceType, l.sourceId, l.expiresAt)

    private fun withUsages(t: PointTransaction) =
        PointTransaction(t.id, t.memberId, t.type, t.amount, t.balanceAfter, t.refType, t.refId, usagesByTx[t.id].orEmpty().toList(), t.createdAt)
}
