package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.benefit.domain.MemberPoint
import com.dogyun.kairos.benefit.domain.PointLot
import com.dogyun.kairos.benefit.domain.PointLotUsage
import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointRepository
import com.dogyun.kairos.benefit.domain.PointSourceType
import com.dogyun.kairos.benefit.domain.PointTransaction
import com.dogyun.kairos.benefit.domain.PointTransactionType
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalDateTime

/**
 * 쓰기를 결정하는 읽기(회원 행, 로트, 사용 내역, 기대하는 원장)는 모두 `FOR UPDATE` 현재 읽기(JDBC)로 한다.
 * 이 서비스들은 주문 생성·취소 같은 더 큰 트랜잭션 안에서도 불리고, 그 트랜잭션이 앞서 일반 읽기를 했다면
 * REPEATABLE READ 스냅샷이 이미 고정돼 있어 일반 읽기는 다른 트랜잭션이 방금 커밋한 로트·원장을 못 본다.
 * JPA 영속성 컨텍스트도 낡은 엔티티를 돌려줄 수 있으므로, 이 값들은 JPA 로 읽지 않고 변경도 절대값 UPDATE(JDBC)로 한다.
 * 새 행 INSERT 는 JPA 를 쓴다. 호출자는 [lockOrCreate] 를 먼저 호출해 회원 행 락을 잡아야 한다.
 */
@Repository
class PointRepositoryImpl(
    private val memberPoints: MemberPointJpaRepository,
    private val lots: PointLotJpaRepository,
    private val transactions: PointTransactionJpaRepository,
    private val usages: PointLotUsageJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
) : PointRepository {

    override fun lockOrCreate(memberId: Long): MemberPoint {
        // 일반 읽기를 먼저 하지 않는다(스냅샷 고정). 없는 행에 FOR UPDATE 를 먼저 걸면 갭 락끼리 INSERT 에서 교착하고,
        // INSERT IGNORE 는 중복 행에 공유 락을 잡아 FOR UPDATE 로 올릴 때 교착한다. ON DUPLICATE KEY UPDATE 는 처음부터 배타 락이다.
        jdbcTemplate.update(
            "insert into member_point (member_id, balance, created_at, created_by, modified_at, modified_by, status) " +
                "values (?, 0, now(6), 0, now(6), 0, 'ACTIVE') on duplicate key update balance = balance",
            memberId,
        )
        return jdbcTemplate.queryForObject(
            "select id, member_id, balance from member_point where member_id = ? and status = 'ACTIVE' for update",
            { rs, _ -> MemberPoint(rs.getLong("id"), rs.getLong("member_id"), rs.getLong("balance")) },
            memberId,
        ) ?: error("member_point 를 만들지 못했다: memberId=$memberId")
    }

    override fun save(point: MemberPoint) {
        jdbcTemplate.update(
            "update member_point set balance = ?, modified_at = now(6) where member_id = ? and status = 'ACTIVE'",
            point.balance, point.memberId,
        )
    }

    /** 조회용 일반 읽기(쓰기를 결정하지 않는다). */
    override fun findByMemberId(memberId: Long): MemberPoint? =
        memberPoints.findByMemberId(memberId)?.let { MemberPoint(it.id, it.memberId, it.balance) }

    override fun findConsumableLots(memberId: Long, now: LocalDateTime): List<PointLot> =
        jdbcTemplate.query(
            "select $LOT_COLUMNS from point_lot where member_id = ? and remaining_amount > 0 and expires_at > ? " +
                "and status = 'ACTIVE' order by expires_at, id for update",
            lotMapper, memberId, now,
        )

    override fun findLotsByIds(ids: Collection<Long>): List<PointLot> {
        if (ids.isEmpty()) return emptyList()
        val marks = ids.joinToString(",") { "?" }
        return jdbcTemplate.query(
            "select $LOT_COLUMNS from point_lot where id in ($marks) and status = 'ACTIVE' order by id for update",
            lotMapper, *ids.toTypedArray(),
        )
    }

    override fun saveLot(lot: PointLot): PointLot {
        val id = lot.id
        if (id != null) {
            jdbcTemplate.update(
                "update point_lot set remaining_amount = ?, modified_at = now(6) where id = ? and status = 'ACTIVE'",
                lot.remainingAmount, id,
            )
            return lot
        }
        val saved = lots.saveAndFlush(PointLotEntity(lot.memberId, lot.amount, lot.remainingAmount, lot.sourceType, lot.sourceId, lot.expiresAt))
        return PointLot(saved.id, lot.memberId, lot.amount, lot.remainingAmount, lot.sourceType, lot.sourceId, lot.expiresAt)
    }

    override fun saveLots(lots: Collection<PointLot>) {
        lots.forEach { saveLot(it) }
    }

    override fun findTransaction(type: PointTransactionType, refType: PointRefType, refId: Long): PointTransaction? {
        val tx = jdbcTemplate.query(
            "select id, member_id, transaction_type, amount, balance_after, ref_type, ref_id, created_at from point_transaction " +
                "where transaction_type = ? and ref_type = ? and ref_id = ? and status = 'ACTIVE' for update",
            { rs, _ -> toTransaction(rs, emptyList()) }, type.name, refType.name, refId,
        ).firstOrNull() ?: return null
        val txUsages = jdbcTemplate.query(
            "select id, lot_id, amount, reverted_amount from point_lot_usage where transaction_id = ? and status = 'ACTIVE' order by id for update",
            { rs, _ -> PointLotUsage(rs.getLong("id"), rs.getLong("lot_id"), rs.getLong("amount"), rs.getLong("reverted_amount")) },
            tx.id,
        )
        return PointTransaction(tx.id, tx.memberId, tx.type, tx.amount, tx.balanceAfter, tx.refType, tx.refId, txUsages, tx.createdAt)
    }

    override fun saveTransactionIfAbsent(tx: PointTransaction): PointTransaction? {
        val keys = GeneratedKeyHolder()
        try {
            jdbcTemplate.update({ con ->
                con.prepareStatement(
                    "insert into point_transaction (member_id, transaction_type, amount, balance_after, ref_type, ref_id, " +
                        "created_at, created_by, modified_at, modified_by, status) values (?, ?, ?, ?, ?, ?, now(6), 0, now(6), 0, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS,
                ).also {
                    it.setLong(1, tx.memberId)
                    it.setString(2, tx.type.name)
                    it.setLong(3, tx.amount)
                    it.setLong(4, tx.balanceAfter)
                    it.setString(5, tx.refType.name)
                    it.setLong(6, tx.refId)
                }
            }, keys)
        } catch (e: DuplicateKeyException) {
            return null
        }
        val txId = requireNotNull(keys.key).toLong()
        val savedUsages = tx.usages.map {
            val u = usages.save(PointLotUsageEntity(txId, it.lotId, it.amount, it.revertedAmount))
            PointLotUsage(u.id, u.lotId, u.amount, u.revertedAmount)
        }
        usages.flush()
        return PointTransaction(txId, tx.memberId, tx.type, tx.amount, tx.balanceAfter, tx.refType, tx.refId, savedUsages)
    }

    override fun saveUsages(usages: Collection<PointLotUsage>) {
        usages.forEach { u ->
            jdbcTemplate.update(
                "update point_lot_usage set reverted_amount = ?, modified_at = now(6) where id = ? and status = 'ACTIVE'",
                u.revertedAmount, requireNotNull(u.id),
            )
        }
    }

    override fun findPageTransactions(memberId: Long, page: Int, size: Int): PageResult<PointTransaction> {
        val result = transactions.findByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size))
        val content = result.content.map {
            PointTransaction(it.id, it.memberId, it.transactionType, it.amount, it.balanceAfter, it.refType, it.refId, emptyList(), it.createdAt)
        }
        return PageResult.of(content, page, size, result.totalElements)
    }

    override fun sumExpiringBetween(memberId: Long, from: LocalDateTime, to: LocalDateTime): Long =
        lots.sumExpiringBetween(memberId, from, to)

    override fun findExpiredLots(now: LocalDateTime, afterId: Long, limit: Int): List<Pair<Long, Long>> =
        lots.findExpired(now, afterId, PageRequest.of(0, limit)).map { it.memberId to it.id }

    override fun findLotForUpdate(lotId: Long): PointLot? =
        jdbcTemplate.query("select $LOT_COLUMNS from point_lot where id = ? and status = 'ACTIVE' for update", lotMapper, lotId).firstOrNull()

    private val lotMapper = RowMapper<PointLot> { rs, _ ->
        PointLot(
            rs.getLong("id"), rs.getLong("member_id"), rs.getLong("amount"), rs.getLong("remaining_amount"),
            PointSourceType.valueOf(rs.getString("source_type")), rs.getLong("source_id"), rs.getObject("expires_at", LocalDateTime::class.java),
        )
    }

    private fun toTransaction(rs: ResultSet, txUsages: List<PointLotUsage>) = PointTransaction(
        rs.getLong("id"), rs.getLong("member_id"), PointTransactionType.valueOf(rs.getString("transaction_type")),
        rs.getLong("amount"), rs.getLong("balance_after"), PointRefType.valueOf(rs.getString("ref_type")), rs.getLong("ref_id"),
        txUsages, rs.getObject("created_at", LocalDateTime::class.java),
    )

    private companion object {
        const val LOT_COLUMNS = "id, member_id, amount, remaining_amount, source_type, source_id, expires_at"
    }
}
