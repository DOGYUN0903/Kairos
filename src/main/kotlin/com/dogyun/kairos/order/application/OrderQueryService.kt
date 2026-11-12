package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.CancelActor
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItem
import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.ReturnStatus
import com.dogyun.kairos.order.domain.SellerOrder
import com.dogyun.kairos.order.domain.SellerOrderStatus
import com.dogyun.kairos.order.domain.ShippingAddress
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 구매자·판매자·관리자용 주문 조회. 남의 주문은 존재 여부를 숨기려 404 로 답한다. */
@Service
@Transactional(readOnly = true)
class OrderQueryService(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val productReader: ProductReader,
    private val properties: OrderProperties,
) {

    /**
     * 환불 미리보기. 주문 행 락 없이 읽는다(쓰기를 결정하는 읽기가 아니다). 계산은 실제 취소와 같은 [Order.planCancel] 이다.
     * 미리보기 뒤 상태가 바뀔 수 있으며 진실은 취소 응답이다.
     */
    fun previewCancel(memberId: Long, orderId: Long, orderItemId: Long): CancelPreviewResult {
        val order = orderRepository.findById(orderId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        order.findItem(orderItemId)
        val plan = order.planCancel(orderItemId, CancelActor.BUYER, properties.shippingPolicy())
        return CancelPreviewResult(
            orderItemId = orderItemId,
            paidAmount = plan.paidAmount,
            shippingFeeRefund = plan.shippingFeeRefund,
            shippingFeeDeducted = plan.shippingFeeDeducted,
            shippingFeeReturned = plan.shippingFeeReturned,
            refundAmount = plan.refundAmount,
            pointsRefund = plan.pointsRefund,
        )
    }

    fun getMine(memberId: Long, orderId: Long): OrderDetail {
        val order = orderRepository.findById(orderId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        return toDetail(order)
    }

    /**
     * 후기 작성 자격 조회(review 모듈용). 남의 항목·없는 항목은 null. 판매자 주문이 `CONFIRMED` 이고 항목이 `ORDERED` 일 때만
     * `confirmedAt` 을 채운다. 구매 확정 뒤에는 취소·상태 변경이 없으므로 일반 읽기로 충분하다.
     */
    fun findConfirmedItem(memberId: Long, orderItemId: Long): ConfirmedItemResult? {
        val order = orderRepository.findByOrderItemId(orderItemId)?.takeIf { it.memberId == memberId } ?: return null
        val sellerOrder = order.sellerOrders.first { so -> so.items.any { it.id == orderItemId } }
        val item = sellerOrder.items.first { it.id == orderItemId }
        val confirmed = sellerOrder.status == SellerOrderStatus.CONFIRMED && item.status == OrderItemStatus.ORDERED
        return ConfirmedItemResult(
            orderId = requireNotNull(order.id),
            orderItemId = orderItemId,
            productId = item.productId,
            sellerId = sellerOrder.sellerId,
            productName = item.productName,
            confirmedAt = if (confirmed) sellerOrder.confirmedAt else null,
        )
    }

    /**
     * 정산용 확정 판매자 주문(settlement 모듈이 포트로 읽는다). `confirmed_at ∈ [from, to)`, id 오름차순 keyset.
     * 확정된 판매자 주문은 더 바뀌지 않으므로 일반 읽기로 충분하고, 정산은 주문 행 락을 잡지 않는다.
     */
    fun findConfirmedForSettlement(from: LocalDateTime, to: LocalDateTime, afterId: Long, limit: Int): List<ConfirmedSellerOrderResult> {
        if (limit < 1) throw DomainException(ErrorCode.INVALID_REQUEST)
        return orderRepository.findSellerOrderIdsConfirmedBetween(from, to, afterId, limit).mapNotNull { sellerOrderId ->
            val order = orderRepository.findBySellerOrderId(sellerOrderId) ?: return@mapNotNull null
            val so = order.findSellerOrder(sellerOrderId)
            ConfirmedSellerOrderResult(
                sellerOrderId = sellerOrderId,
                sellerId = so.sellerId,
                orderId = requireNotNull(order.id),
                orderNo = order.orderNo,
                confirmedAt = requireNotNull(so.confirmedAt) { "CONFIRMED 판매자 주문에 confirmedAt 이 없다: $sellerOrderId" },
                commissionRateBp = so.commissionRateBp,
                shippingFee = so.shippingFee,
                buyerShippingFeeRefunded = so.buyerShippingFeeRefunded,
                items = so.items.map { item ->
                    ConfirmedSellerOrderItemResult(
                        orderItemId = requireNotNull(item.id),
                        status = item.status.name,
                        lineAmount = item.lineAmount,
                        productCouponDiscount = item.productCouponDiscount,
                        productCouponIssuer = item.productCouponIssuer?.name,
                        sellerBorneReturnFee = item.returnRequest?.takeIf { it.status == ReturnStatus.COMPLETED }?.sellerBorneFee ?: 0L,
                    )
                },
            )
        }
    }

    fun listMine(memberId: Long, page: Int, size: Int): PageResult<OrderSummary> {
        validate(page, size)
        return toSummaries(orderRepository.findPageByMemberId(memberId, page, size))
    }

    /** 결제 전(PENDING_PAYMENT) 판매자 주문은 판매자에게 없는 것으로 친다. */
    fun getForSeller(sellerId: Long, sellerOrderId: Long): SellerOrderDetail {
        val order = orderRepository.findBySellerOrderId(sellerOrderId) ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
        val sellerOrder = order.findSellerOrder(sellerOrderId)
        if (sellerOrder.sellerId != sellerId || sellerOrder.status == SellerOrderStatus.PENDING_PAYMENT) {
            throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
        }
        val thumbnails = thumbnailsOf(sellerOrder.items)
        return SellerOrderDetail(
            sellerOrderId = sellerOrderId,
            orderId = requireNotNull(order.id),
            orderNo = order.orderNo,
            status = sellerOrder.status.name,
            shippingFee = sellerOrder.shippingFee,
            shippingFeePayer = sellerOrder.shippingFeePayer.name,
            carrierCode = sellerOrder.carrierCode,
            trackingNumber = sellerOrder.trackingNumber,
            shippedAt = sellerOrder.shippedAt,
            deliveredAt = sellerOrder.deliveredAt,
            confirmedAt = sellerOrder.confirmedAt,
            returnableUntil = sellerOrder.returnableUntil(properties.returnWindow),
            allItemsReturned = sellerOrder.allItemsReturned,
            hasOpenReturn = sellerOrder.hasOpenReturn,
            shippingAddress = order.shippingAddress.toResult(),
            items = sellerOrder.items.map { it.toResult(thumbnails) },
            orderedAt = requireNotNull(order.createdAt),
        )
    }

    fun listForSeller(sellerId: Long, status: SellerOrderStatus?, page: Int, size: Int): PageResult<SellerOrderSummary> {
        validate(page, size)
        val orders = orderRepository.findPageBySellerId(sellerId, status, page, size)
        val thumbnails = thumbnailsOf(orders.content.flatMap { o -> o.sellerOrders.first { it.sellerId == sellerId }.items.take(1) })
        return orders.map { order ->
            val sellerOrder = order.sellerOrders.first { it.sellerId == sellerId }
            SellerOrderSummary(
                sellerOrderId = requireNotNull(sellerOrder.id),
                orderNo = order.orderNo,
                status = sellerOrder.status.name,
                itemsAmount = sellerOrder.itemsAmount,
                itemCount = sellerOrder.items.size,
                firstProductName = sellerOrder.items.first().productName,
                thumbnailUrl = thumbnails[sellerOrder.items.first().productId],
                orderedAt = requireNotNull(order.createdAt),
            )
        }
    }

    fun getForAdmin(orderId: Long): OrderDetail =
        toDetail(orderRepository.findById(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND))

    fun listForAdmin(status: OrderStatus?, memberId: Long?, orderNo: String?, page: Int, size: Int): PageResult<OrderSummary> {
        validate(page, size)
        return toSummaries(orderRepository.findPageForAdmin(status, memberId, orderNo, page, size))
    }

    private fun validate(page: Int, size: Int) {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
    }

    private fun toDetail(order: Order): OrderDetail {
        val orderId = requireNotNull(order.id)
        val thumbnails = thumbnailsOf(order.allItems)
        return OrderDetail(
            orderId = orderId,
            orderNo = order.orderNo,
            status = order.status.name,
            itemsAmount = order.itemsAmount,
            buyerShippingFee = order.buyerShippingFee,
            totalAmount = order.totalAmount,
            freeShippingApplied = order.freeShippingApplied,
            shippingFeeRecovered = order.shippingFeeRecovered,
            refundedAmount = order.refundedAmount,
            remainingAmount = order.totalAmount - order.refundedAmount,
            remainingPaidAmount = order.remainingPaidAmount,
            remainingBuyerShippingFee = order.remainingBuyerShippingFee,
            productCouponDiscount = order.productCouponDiscount,
            cartCouponId = order.cartCouponId,
            cartCouponDiscount = order.cartCouponDiscount,
            pointsUsed = order.pointsUsed,
            pointsRefunded = order.pointsRefunded,
            expiresAt = order.expiresAt,
            paidAt = order.paidAt,
            createdAt = requireNotNull(order.createdAt),
            shippingAddress = order.shippingAddress.toResult(),
            sellerOrders = order.sellerOrders.map { it.toResult(thumbnails) },
            payment = findPayment(orderId),
        )
    }

    private fun findPayment(orderId: Long): PaymentResult? = try {
        paymentGateway.get(orderId).let { PaymentResult(it.paymentKey, it.amount, it.refundedAmount, it.status) }
    } catch (e: DomainException) {
        if (e.errorCode == ErrorCode.PAYMENT_NOT_FOUND) null else throw e
    }

    /** 한 페이지의 첫 항목 이미지를 한 번에 조회한다(N+1 금지). 판매자 주문 상태는 이미 읽은 애그리거트에서 만든다. */
    private fun toSummaries(page: PageResult<Order>): PageResult<OrderSummary> {
        val thumbnails = thumbnailsOf(page.content.map { it.allItems.first() })
        return page.map { toSummary(it, thumbnails) }
    }

    /** 상품 ID → 대표 이미지 URL. 항목이 없으면 조회하지 않고, 상품이 사라졌으면 맵에 없다(호출부가 null 로 쓴다). */
    private fun thumbnailsOf(items: Collection<OrderItem>): Map<Long, String> {
        val productIds = items.map { it.productId }.toSet()
        return if (productIds.isEmpty()) emptyMap() else productReader.findThumbnailUrls(productIds)
    }

    private fun toSummary(order: Order, thumbnails: Map<Long, String>) = OrderSummary(
        orderId = requireNotNull(order.id),
        orderNo = order.orderNo,
        status = order.status.name,
        totalAmount = order.totalAmount,
        itemCount = order.allItems.size,
        firstProductName = order.allItems.first().productName,
        thumbnailUrl = thumbnails[order.allItems.first().productId],
        sellerOrderStatuses = order.sellerOrders.map { it.status.name },
        expiresAt = order.expiresAt,
        createdAt = requireNotNull(order.createdAt),
    )

    private fun SellerOrder.toResult(thumbnails: Map<Long, String>) = SellerOrderResult(
        sellerOrderId = requireNotNull(id),
        sellerId = sellerId,
        sellerName = sellerName,
        status = status.name,
        shippingFee = shippingFee,
        shippingFeePayer = shippingFeePayer.name,
        carrierCode = carrierCode,
        trackingNumber = trackingNumber,
        shippedAt = shippedAt,
        deliveredAt = deliveredAt,
        confirmedAt = confirmedAt,
        returnableUntil = returnableUntil(properties.returnWindow),
        allItemsReturned = allItemsReturned,
        hasOpenReturn = hasOpenReturn,
        items = items.map { it.toResult(thumbnails) },
    )

    private fun OrderItem.toResult(thumbnails: Map<Long, String>) = OrderItemResult(
        orderItemId = requireNotNull(id),
        skuId = skuId,
        productId = productId,
        productName = productName,
        thumbnailUrl = thumbnails[productId],
        brandName = brandName,
        optionDisplayName = optionDisplayName,
        salePrice = salePrice,
        extraPrice = extraPrice,
        quantity = quantity,
        lineAmount = lineAmount,
        status = status.name,
        cancelActor = cancelActor?.name,
        cancelReason = cancelReason,
        refundAmount = refundAmount,
        productCouponId = productCouponId,
        productCouponDiscount = productCouponDiscount,
        productCouponIssuer = productCouponIssuer?.name,
        cartCouponAllocated = cartCouponAllocated,
        pointsAllocated = pointsAllocated,
        paidAmount = paidAmount,
        pointsRefunded = pointsRefunded,
        returnRequest = returnRequest?.toSummary(),
    )

    private fun ShippingAddress.toResult() = ShippingAddressResult(receiverName, phone, zipCode, address1, address2, memo)

    private fun <T, R> PageResult<T>.map(transform: (T) -> R) =
        PageResult(content.map(transform), page, size, totalElements, totalPages, hasNext)

    private companion object {
        const val MAX_SIZE = 100
    }
}
