package com.dogyun.kairos.benefit.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

interface CouponPolicyRepository {
    /** 새 정책은 INSERT, 기존 정책은 상태만 갱신한다(발급 수량은 [tryIncreaseIssued] 만 바꾼다). */
    fun save(policy: CouponPolicy): CouponPolicy
    fun findById(id: Long): CouponPolicy?
    fun findAllByIds(ids: Collection<Long>): List<CouponPolicy>

    /**
     * 발급 가능한 정책: ACTIVE·기간 안·수량 미소진이며 장바구니 쿠폰, ALL_PRODUCTS, [productId] 의 PRODUCT,
     * [sellerId] 의 SELLER 정책. productId/sellerId 가 null 이면 앞의 두 종류만.
     */
    fun findClaimable(productId: Long?, sellerId: Long?, now: LocalDateTime, page: Int, size: Int): PageResult<CouponPolicy>
    fun findPageByIssuer(issuerType: IssuerType, sellerId: Long?, page: Int, size: Int): PageResult<CouponPolicy>

    /** `UPDATE … SET issued_count = issued_count + 1 WHERE id = ? AND (total_quantity IS NULL OR issued_count < total_quantity)` 의 영향 행 수가 1 인지. */
    fun tryIncreaseIssued(policyId: Long): Boolean
}
