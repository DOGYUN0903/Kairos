package com.dogyun.kairos.benefit.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

interface PointRepository {
    /** 회원 포인트 행을 `FOR UPDATE` 로 잠가 읽는다. 없으면 만들고 다시 잠근다. */
    fun lockOrCreate(memberId: Long): MemberPoint
    fun save(point: MemberPoint)
    fun findByMemberId(memberId: Long): MemberPoint?

    /** `remaining > 0 AND expires_at > now`, 만료 빠른 순(같으면 id 순). */
    fun findConsumableLots(memberId: Long, now: LocalDateTime): List<PointLot>
    fun findLotsByIds(ids: Collection<Long>): List<PointLot>
    fun saveLot(lot: PointLot): PointLot
    fun saveLots(lots: Collection<PointLot>)

    /** `FOR UPDATE` 현재 읽기. 사용 거래라면 [PointTransaction.usages] 가 채워져 있다. 있다고 기대하는 거래를 읽을 때만 쓴다(없음 확인은 [saveTransactionIfAbsent]). */
    fun findTransaction(type: PointTransactionType, refType: PointRefType, refId: Long): PointTransaction?

    /**
     * 새 거래와 그 [PointTransaction.usages] 를 함께 저장한다. 같은 (type, refType, refId) 가 이미 있으면 아무것도 저장하지 않고 null.
     * 없음을 먼저 읽어 확인하는 대신 INSERT 의 UNIQUE 충돌로 판정한다(없는 키를 `FOR UPDATE` 로 읽으면 갭 락끼리 교착한다).
     */
    fun saveTransactionIfAbsent(tx: PointTransaction): PointTransaction?

    /** 이미 저장된 사용 내역(id 있음)의 `reverted_amount` 를 갱신한다. */
    fun saveUsages(usages: Collection<PointLotUsage>)
    fun findPageTransactions(memberId: Long, page: Int, size: Int): PageResult<PointTransaction>

    /** `from < expires_at <= to` 인 로트의 남은 양 합. */
    fun sumExpiringBetween(memberId: Long, from: LocalDateTime, to: LocalDateTime): Long

    /** 만료됐고 남은 양이 있는 로트의 (memberId, lotId), `id > afterId` 오름차순. */
    fun findExpiredLots(now: LocalDateTime, afterId: Long, limit: Int): List<Pair<Long, Long>>

    /** 로트를 `FOR UPDATE` 로 읽는다. 호출 전에 회원 행 락을 잡아야 한다. */
    fun findLotForUpdate(lotId: Long): PointLot?
}
