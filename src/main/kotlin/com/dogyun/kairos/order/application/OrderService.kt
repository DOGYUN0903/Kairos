package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.application.TransactionRunner
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.CouponDiscountType
import com.dogyun.kairos.order.domain.CouponIssuer
import com.dogyun.kairos.order.domain.CouponKind
import com.dogyun.kairos.order.domain.CouponTarget
import com.dogyun.kairos.order.domain.CouponTerms
import com.dogyun.kairos.order.domain.DiscountAllocation
import com.dogyun.kairos.order.domain.DiscountAllocator
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderLine
import com.dogyun.kairos.order.domain.OrderNo
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.ShippingAddress
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 주문 생성과 금액 미리보기. 트랜잭션은 [TransactionRunner] 로 직접 연다(`@Transactional` 없음):
 * 0원 주문의 승인 확정은 생성 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 해야 하기 때문이다(ADR 0004).
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productReader: ProductReader,
    private val sellerReader: SellerReader,
    private val stockManager: StockManager,
    private val paymentGateway: PaymentGateway,
    private val benefitApplier: BenefitApplier,
    private val orderPaymentService: OrderPaymentService,
    private val properties: OrderProperties,
    private val tx: TransactionRunner,
    private val clock: Clock,
    private val random: Random = Random.Default,
) {

    /**
     * 주문을 PENDING_PAYMENT 로 만들고 쿠폰·포인트를 적용하고 재고를 차감하고 결제(READY)를 준비한다. 한 트랜잭션이라 실패하면 전부 롤백된다.
     * 총액이 0원이면 커밋 뒤 PG 없이 승인을 확정해 PAID 로 만든다.
     */
    fun place(memberId: Long, cmd: PlaceOrderCommand): PlaceOrderResult {
        val placed = tx.run { placeInTx(memberId, cmd) }
        if (placed.totalAmount != 0L) return placed
        orderPaymentService.approveWithoutPg(placed.orderId)
        return tx.run {
            val order = orderRepository.findById(placed.orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
            placed.copy(orderStatus = order.status.name, paymentStatus = paymentGateway.get(placed.orderId).status)
        }
    }

    /** 락 순서(ADR 0004): `member_point → coupon`(reserve) → `sku`(재고 차감). 주문 행은 INSERT 라 잠그지 않는다. */
    private fun placeInTx(memberId: Long, cmd: PlaceOrderCommand): PlaceOrderResult {
        val quantities = mergeQuantities(cmd.items)
        val couponIds = validateCoupons(quantities.keys, cmd.productCoupons, cmd.cartCouponId)
        val lines = snapshot(quantities)

        val reservation = benefitApplier.reserve(memberId, couponIds)
        val allocation = allocate(lines, reservation, cmd.productCoupons, cmd.cartCouponId, cmd.pointsToUse)
        val now = LocalDateTime.now(clock)
        val order = Order.create(
            orderNo = OrderNo.generate(now, random),
            memberId = memberId,
            lines = lines,
            allocation = allocation,
            address = cmd.shippingAddress.toDomain(),
            policy = properties.shippingPolicy(),
            now = now,
            paymentTtl = properties.paymentTtl,
        )
        if (order.totalAmount != cmd.expectedTotalAmount) throw DomainException(ErrorCode.ORDER_PRICE_CHANGED)

        stockManager.decrease(quantities.map { (skuId, quantity) -> SkuQuantity(skuId, quantity) })
        // 재고 차감(조건부 UPDATE)이 영속성 컨텍스트를 비우므로(clearAutomatically) 주문은 반드시 차감 뒤에 저장한다.
        // 먼저 저장하면 비워지는 시점에 flush 되지 않은 변경이 사라질 수 있다.
        val saved = orderRepository.save(order)
        val orderId = requireNotNull(saved.id)
        benefitApplier.commit(memberId, orderId, couponIds, cmd.pointsToUse)
        paymentGateway.prepare(orderId, saved.orderNo, saved.totalAmount)
        return PlaceOrderResult(
            orderId, saved.orderNo, saved.totalAmount, saved.expiresAt, OrderStatus.PENDING_PAYMENT.name, "READY",
        )
    }

    /** place 와 같은 계산으로 금액을 미리 보여 준다. 락·UPDATE·저장이 없다. */
    fun quote(memberId: Long, cmd: QuoteOrderCommand): OrderQuote = tx.run {
        val quantities = mergeQuantities(cmd.items)
        val couponIds = validateCoupons(quantities.keys, cmd.productCoupons, cmd.cartCouponId)
        val lines = snapshot(quantities)
        val reservation = benefitApplier.quote(memberId, couponIds)
        val allocation = allocate(lines, reservation, cmd.productCoupons, cmd.cartCouponId, cmd.pointsToUse)
        val now = LocalDateTime.now(clock)
        // 금액·배송비 계산을 place 와 똑같이 하려고 주문을 만들어 보기만 한다(저장하지 않는다).
        val order = Order.create(
            orderNo = OrderNo.generate(now, random),
            memberId = memberId,
            lines = lines,
            allocation = allocation,
            address = QUOTE_ADDRESS,
            policy = properties.shippingPolicy(),
            now = now,
            paymentTtl = properties.paymentTtl,
        )
        // 표시용 필드는 도메인 OrderLine 에 넣지 않고 SKU 조회 결과를 skuId 로 다시 매핑한다.
        val skuInfos = productReader.findOrderableSkus(quantities.keys)
        val policy = properties.shippingPolicy()
        val afterCoupon = order.itemsAmount - allocation.productCouponDiscount - allocation.cartCouponDiscount
        OrderQuote(
            items = order.allItems.sortedBy { it.skuId }.map {
                QuoteLine(
                    skuId = it.skuId, productId = it.productId, productName = it.productName, brandName = it.brandName,
                    optionDisplayName = it.optionDisplayName, thumbnailUrl = skuInfos.getValue(it.skuId).thumbnailUrl,
                    unitPrice = it.salePrice + it.extraPrice, sellerId = skuInfos.getValue(it.skuId).sellerId,
                    quantity = it.quantity, lineAmount = it.lineAmount, productCouponDiscount = it.productCouponDiscount,
                    cartCouponAllocated = it.cartCouponAllocated, pointsAllocated = it.pointsAllocated, paidAmount = it.paidAmount,
                )
            },
            sellerQuotes = order.sellerOrders.map {
                SellerQuote(it.sellerId, it.sellerName, it.itemsAmount, it.shippingFee, it.shippingFeePayer.name)
            },
            freeShippingThreshold = policy.freeShippingThreshold,
            baseShippingFee = policy.baseShippingFee,
            itemsAmount = order.itemsAmount,
            productCouponDiscount = allocation.productCouponDiscount,
            cartCouponDiscount = allocation.cartCouponDiscount,
            pointsUsed = allocation.pointsUsed,
            buyerShippingFee = order.buyerShippingFee,
            totalAmount = order.totalAmount,
            maxUsablePoints = DiscountAllocator.maxUsablePoints(afterCoupon, reservation.pointBalance, reservation.pointPolicy),
        )
    }

    /**
     * 쿠폰 지정 검증(benefit 을 부르기 전): 같은 쿠폰 ID 중복(상품 쿠폰끼리·장바구니와), 한 SKU 에 상품 쿠폰 두 장,
     * 항목에 없는 SKU 지정은 `ORDER_INVALID`. 쓸 쿠폰 ID 전부를 돌려준다.
     */
    private fun validateCoupons(skuIds: Set<Long>, productCoupons: List<ProductCouponCommand>, cartCouponId: Long?): List<Long> {
        val couponIds = productCoupons.map { it.couponId } + listOfNotNull(cartCouponId)
        if (couponIds.toSet().size != couponIds.size) throw DomainException(ErrorCode.ORDER_INVALID)
        if (productCoupons.map { it.skuId }.toSet().size != productCoupons.size) throw DomainException(ErrorCode.ORDER_INVALID)
        if (productCoupons.any { it.skuId !in skuIds }) throw DomainException(ErrorCode.ORDER_INVALID)
        return couponIds
    }

    private fun snapshot(quantities: Map<Long, Int>): List<OrderLine> {
        val skus = productReader.findOrderableSkus(quantities.keys)
        if (quantities.keys.any { skus[it]?.available != true }) throw DomainException(ErrorCode.SKU_NOT_ORDERABLE)
        val sellers = sellerReader.findSellers(skus.values.map { it.sellerId }.toSet())
        return quantities.map { (skuId, quantity) ->
            val sku = skus.getValue(skuId)
            val seller = sellers[sku.sellerId] ?: throw DomainException(ErrorCode.SKU_NOT_ORDERABLE)
            OrderLine(
                skuId = sku.skuId, productId = sku.productId, productName = sku.productName, brandName = sku.brandName,
                optionDisplayName = sku.optionDisplayName, salePrice = sku.salePrice, extraPrice = sku.extraPrice,
                sellerId = sku.sellerId, sellerName = seller.companyName, commissionRateBp = seller.commissionRateBp,
                quantity = quantity,
            )
        }
    }

    private fun allocate(
        lines: List<OrderLine>,
        reservation: BenefitReservation,
        productCoupons: List<ProductCouponCommand>,
        cartCouponId: Long?,
        pointsToUse: Long,
    ): DiscountAllocation = DiscountAllocator.allocate(
        lines = lines,
        productCoupons = productCoupons.associate { it.skuId to reservation.coupons.getValue(it.couponId).toTerms() },
        cartCoupon = cartCouponId?.let { reservation.coupons.getValue(it).toTerms() },
        pointsToUse = pointsToUse,
        pointBalance = reservation.pointBalance,
        policy = reservation.pointPolicy,
    )

    private fun ReservedCoupon.toTerms() = CouponTerms(
        couponId = couponId,
        kind = CouponKind.valueOf(couponType),
        issuer = CouponIssuer.valueOf(issuerType),
        target = CouponTarget.valueOf(targetType),
        targetProductId = targetProductId,
        targetSellerId = sellerId,
        discountType = CouponDiscountType.valueOf(discountType),
        discountValue = discountValue,
        maxDiscount = maxDiscount,
        minOrderAmount = minOrderAmount,
    )

    /**
     * 같은 SKU 는 수량을 합치고 SKU ID 오름차순으로 돌려준다(할인 배분이 SKU ID 를 키로 쓰므로 항목은 SKU 당 하나다).
     * 항목이 없거나 수량이 1..999 밖이면 ORDER_INVALID.
     */
    private fun mergeQuantities(items: List<OrderItemCommand>): Map<Long, Int> {
        if (items.isEmpty() || items.any { it.quantity !in QUANTITY_RANGE }) throw DomainException(ErrorCode.ORDER_INVALID)
        val merged = items.groupBy { it.skuId }.mapValues { (_, group) -> group.sumOf { it.quantity } }
        if (merged.values.any { it !in QUANTITY_RANGE }) throw DomainException(ErrorCode.ORDER_INVALID)
        return merged.toSortedMap()
    }

    private fun ShippingAddressCommand.toDomain() = ShippingAddress(receiverName, phone, zipCode, address1, address2, memo)

    private companion object {
        val QUANTITY_RANGE = 1..999

        /** 미리보기용 자리표시 배송지(배송지 검증을 통과하기만 하면 된다). */
        val QUOTE_ADDRESS = ShippingAddress("미리보기", "000-0000-0000", "00000", "미리보기", "미리보기", null)
    }
}
