package com.dogyun.kairos.benefit.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface CouponPolicyJpaRepository : JpaRepository<CouponPolicyEntity, Long> {

    /** 엔티티는 issued_count 를 쓰지 않으므로(updatable=false) native 조건부 UPDATE 로 올린다. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update coupon_policy set issued_count = issued_count + 1 " +
            "where id = :policyId and status = 'ACTIVE' and (total_quantity is null or issued_count < total_quantity)",
        nativeQuery = true,
    )
    fun increaseIssued(@Param("policyId") policyId: Long): Int
}
