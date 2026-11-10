package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.CouponPolicy
import com.dogyun.kairos.benefit.domain.CouponPolicyRepository
import com.dogyun.kairos.benefit.domain.CouponType
import com.dogyun.kairos.benefit.domain.DiscountType
import com.dogyun.kairos.benefit.domain.IssuerType
import com.dogyun.kairos.benefit.domain.TargetType
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CouponPolicyService(
    private val policyRepository: CouponPolicyRepository,
    private val productReader: BenefitProductReader,
    @Suppress("unused") private val clock: Clock,
) {

    @Transactional
    fun createByAdmin(command: CreateCouponPolicyCommand): Long {
        val policy = build(command, IssuerType.PLATFORM, null, parseEnum<CouponType>(command.couponType))
        policy.targetProductId?.let { requireProduct(it) }
        return requireNotNull(policyRepository.save(policy).id)
    }

    /** 판매자 정책은 자기 돈만 쓰도록 `PRODUCT` 쿠폰으로 고정하고, 상품 대상이면 그 상품이 자기 것이어야 한다. */
    @Transactional
    fun createBySeller(sellerId: Long, command: CreateCouponPolicyCommand): Long {
        val policy = build(command, IssuerType.SELLER, sellerId, CouponType.PRODUCT)
        policy.targetProductId?.let { productId ->
            if (requireProduct(productId).sellerId != sellerId) throw DomainException(ErrorCode.COUPON_POLICY_INVALID)
        }
        return requireNotNull(policyRepository.save(policy).id)
    }

    /** 이미 종료된 정책이면 그대로 둔다. 발급된 쿠폰은 만료일까지 유효하다. */
    @Transactional
    fun end(policyId: Long) {
        val policy = policyRepository.findById(policyId) ?: throw DomainException(ErrorCode.COUPON_POLICY_NOT_FOUND)
        policy.end()
        policyRepository.save(policy)
    }

    @Transactional
    fun endBySeller(sellerId: Long, policyId: Long) {
        val policy = policyRepository.findById(policyId)
            ?.takeIf { it.issuerType == IssuerType.SELLER && it.sellerId == sellerId }
            ?: throw DomainException(ErrorCode.COUPON_POLICY_NOT_FOUND)
        policy.end()
        policyRepository.save(policy)
    }

    @Transactional(readOnly = true)
    fun listForAdmin(page: Int, size: Int): PageResult<CouponPolicyResult> =
        toResults(policyRepository.findPageByIssuer(IssuerType.PLATFORM, null, checkedPage(page), checkedSize(size)))

    @Transactional(readOnly = true)
    fun listForSeller(sellerId: Long, page: Int, size: Int): PageResult<CouponPolicyResult> =
        toResults(policyRepository.findPageByIssuer(IssuerType.SELLER, sellerId, checkedPage(page), checkedSize(size)))

    private fun toResults(result: PageResult<CouponPolicy>) =
        PageResult(result.content.map { it.toResult(false) }, result.page, result.size, result.totalElements, result.totalPages, result.hasNext)

    private fun requireProduct(productId: Long): ProductRef =
        productReader.findProduct(productId) ?: throw DomainException(ErrorCode.COUPON_POLICY_INVALID)

    private fun build(command: CreateCouponPolicyCommand, issuer: IssuerType, sellerId: Long?, couponType: CouponType) =
        CouponPolicy.create(
            name = command.name,
            issuerType = issuer,
            sellerId = sellerId,
            couponType = couponType,
            targetType = parseEnum<TargetType>(command.targetType),
            targetProductId = command.targetProductId,
            discountType = parseEnum<DiscountType>(command.discountType),
            discountValue = command.discountValue,
            maxDiscount = command.maxDiscount,
            minOrderAmount = command.minOrderAmount,
            totalQuantity = command.totalQuantity,
            validityDays = command.validityDays,
            validFrom = command.validFrom,
            validUntil = command.validUntil,
        )
}

internal fun checkedPage(page: Int): Int = if (page < 0) throw DomainException(ErrorCode.INVALID_REQUEST) else page

internal fun checkedSize(size: Int): Int = if (size !in 1..MAX_PAGE_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST) else size

private const val MAX_PAGE_SIZE = 100

private inline fun <reified E : Enum<E>> parseEnum(name: String): E =
    enumValues<E>().firstOrNull { it.name == name } ?: throw DomainException(ErrorCode.COUPON_POLICY_INVALID)
