package com.dogyun.kairos.benefit.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

interface CouponRepository {
    /** 같은 (정책, 회원) 이 이미 있으면 UNIQUE 위반으로 `DataIntegrityViolationException` 이 난다. */
    fun save(coupon: Coupon): Coupon
    fun findById(id: Long): Coupon?
    fun findAllByIds(ids: Collection<Long>): List<Coupon>
    fun findByPolicyAndMember(policyId: Long, memberId: Long): Coupon?

    /** [policyIds] 중 [memberId] 가 이미 받은 정책 ID. */
    fun findClaimedPolicyIds(memberId: Long, policyIds: Collection<Long>): Set<Long>

    /** [status] 는 계산값(`AVAILABLE`/`USED`/`EXPIRED`) 필터, null 이면 전체. 최근 발급 순. */
    fun findPageByMember(memberId: Long, status: String?, now: LocalDateTime, page: Int, size: Int): PageResult<Coupon>

    /** `AVAILABLE`·본인·만료 전일 때만 `USED` 로 바꾸고 true. */
    fun markUsed(couponId: Long, memberId: Long, now: LocalDateTime): Boolean
    fun attachOrder(couponIds: Collection<Long>, orderId: Long)

    /** `USED → AVAILABLE`, 주문·사용 시각 비움. 만료됐으면 돌리지 않고 false. */
    fun restoreIfNotExpired(couponId: Long, now: LocalDateTime): Boolean
    fun findUsedByOrderId(orderId: Long): List<Coupon>
}
