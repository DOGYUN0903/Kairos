package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.Coupon
import com.dogyun.kairos.benefit.domain.CouponPolicyRepository
import com.dogyun.kairos.benefit.domain.CouponRepository
import com.dogyun.kairos.benefit.domain.CouponStatus
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class CouponService(
    private val policyRepository: CouponPolicyRepository,
    private val couponRepository: CouponRepository,
    private val clock: Clock,
) {

    /**
     * 수량은 조건부 UPDATE 의 영향 행 수로 판정한다(락 없음). 이미 받은 회원은 수량을 올리기 전에 거른다.
     * 동시 요청이 UNIQUE(policy_id, member_id) 에 걸리면 트랜잭션이 롤백되어 올린 `issued_count` 도 함께 돌아간다.
     */
    @Transactional
    fun claim(memberId: Long, policyId: Long): ClaimedCoupon {
        val now = LocalDateTime.now(clock)
        val policy = policyRepository.findById(policyId) ?: throw DomainException(ErrorCode.COUPON_POLICY_NOT_FOUND)
        policy.requireClaimable(now)
        if (couponRepository.findByPolicyAndMember(policyId, memberId) != null) {
            throw DomainException(ErrorCode.COUPON_ALREADY_ISSUED)
        }
        if (!policyRepository.tryIncreaseIssued(policyId)) throw DomainException(ErrorCode.COUPON_SOLD_OUT)
        val coupon = try {
            couponRepository.save(Coupon(null, policyId, memberId, CouponStatus.AVAILABLE, null, now, policy.expiresAtFor(now), null))
        } catch (e: DataIntegrityViolationException) {
            throw DomainException(ErrorCode.COUPON_ALREADY_ISSUED)
        }
        return ClaimedCoupon(requireNotNull(coupon.id), coupon.expiresAt)
    }
}
