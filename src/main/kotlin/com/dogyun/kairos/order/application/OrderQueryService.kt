package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderItem
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrder
import com.dogyun.kairos.order.domain.SellerOrderStatus
import com.dogyun.kairos.order.domain.ShippingAddress
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 구매자·판매자·관리자용 주문 조회. 남의 주문은 존재 여부를 숨기려 404 로 답한다. */
@Service
@Transactional(readOnly = true)
class OrderQueryService(
    private val orderRepository: OrderRepository,
    private val paymentGateway: PaymentGateway,
) {

    fun getMine(memberId: Long, orderId: Long): OrderDetail {
        val order = orderRepository.findById(orderId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        return toDetail(order)
    }

    fun listMine(memberId: Long, page: Int, size: Int): PageResult<OrderSummary> {
        validate(page, size)
        return orderRepository.findPageByMemberId(memberId, page, size).map(::toSummary)
    }

    /** 결제 전(PENDING_PAYMENT) 판매자 주문은 판매자에게 없는 것으로 친다. */
    fun getForSeller(sellerId: Long, sellerOrderId: Long): SellerOrderDetail {
        val order = orderRepository.findBySellerOrderId(sellerOrderId) ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
        val sellerOrder = order.findSellerOrder(sellerOrderId)
        if (sellerOrder.sellerId != sellerId || sellerOrder.status == SellerOrderStatus.PENDING_PAYMENT) {
            throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
        }
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
            shippingAddress = order.shippingAddress.toResult(),
            items = sellerOrder.items.map { it.toResult() },
            orderedAt = requireNotNull(order.createdAt),
        )
    }

    fun listForSeller(sellerId: Long, status: SellerOrderStatus?, page: Int, size: Int): PageResult<SellerOrderSummary> {
        validate(page, size)
        return orderRepository.findPageBySellerId(sellerId, status, page, size).map { order ->
            val sellerOrder = order.sellerOrders.first { it.sellerId == sellerId }
            SellerOrderSummary(
                sellerOrderId = requireNotNull(sellerOrder.id),
                orderNo = order.orderNo,
                status = sellerOrder.status.name,
                itemsAmount = sellerOrder.itemsAmount,
                itemCount = sellerOrder.items.size,
                firstProductName = sellerOrder.items.first().productName,
                orderedAt = requireNotNull(order.createdAt),
            )
        }
    }

    fun getForAdmin(orderId: Long): OrderDetail =
        toDetail(orderRepository.findById(orderId) ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND))

    fun listForAdmin(status: OrderStatus?, memberId: Long?, orderNo: String?, page: Int, size: Int): PageResult<OrderSummary> {
        validate(page, size)
        return orderRepository.findPageForAdmin(status, memberId, orderNo, page, size).map(::toSummary)
    }

    private fun validate(page: Int, size: Int) {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
    }

    private fun toDetail(order: Order): OrderDetail {
        val orderId = requireNotNull(order.id)
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
            expiresAt = order.expiresAt,
            paidAt = order.paidAt,
            createdAt = requireNotNull(order.createdAt),
            shippingAddress = order.shippingAddress.toResult(),
            sellerOrders = order.sellerOrders.map { it.toResult() },
            payment = findPayment(orderId),
        )
    }

    private fun findPayment(orderId: Long): PaymentResult? = try {
        paymentGateway.get(orderId).let { PaymentResult(it.paymentKey, it.amount, it.refundedAmount, it.status) }
    } catch (e: DomainException) {
        if (e.errorCode == ErrorCode.PAYMENT_NOT_FOUND) null else throw e
    }

    private fun toSummary(order: Order) = OrderSummary(
        orderId = requireNotNull(order.id),
        orderNo = order.orderNo,
        status = order.status.name,
        totalAmount = order.totalAmount,
        itemCount = order.allItems.size,
        firstProductName = order.allItems.first().productName,
        createdAt = requireNotNull(order.createdAt),
    )

    private fun SellerOrder.toResult() = SellerOrderResult(
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
        items = items.map { it.toResult() },
    )

    private fun OrderItem.toResult() = OrderItemResult(
        orderItemId = requireNotNull(id),
        skuId = skuId,
        productId = productId,
        productName = productName,
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
    )

    private fun ShippingAddress.toResult() = ShippingAddressResult(receiverName, phone, zipCode, address1, address2, memo)

    private fun <T, R> PageResult<T>.map(transform: (T) -> R) =
        PageResult(content.map(transform), page, size, totalElements, totalPages, hasNext)

    private companion object {
        const val MAX_SIZE = 100
    }
}
