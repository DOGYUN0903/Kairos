package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.DiscountAllocation
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderLine
import com.dogyun.kairos.order.domain.OrderNo
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.ShippingAddress
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import kotlin.random.Random

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productReader: ProductReader,
    private val sellerReader: SellerReader,
    private val stockManager: StockManager,
    private val paymentGateway: PaymentGateway,
    private val properties: OrderProperties,
    private val clock: Clock,
    private val random: Random = Random.Default,
) {

    /** 주문을 PENDING_PAYMENT 로 만들고 재고를 차감하고 결제(READY)를 준비한다. 한 트랜잭션이라 실패하면 전부 롤백된다. */
    @Transactional
    fun place(memberId: Long, cmd: PlaceOrderCommand): PlaceOrderResult {
        val quantities = mergeQuantities(cmd.items)
        val skus = productReader.findOrderableSkus(quantities.keys)
        if (quantities.keys.any { skus[it]?.available != true }) throw DomainException(ErrorCode.SKU_NOT_ORDERABLE)
        val sellers = sellerReader.findSellers(skus.values.map { it.sellerId }.toSet())

        val now = LocalDateTime.now(clock)
        val lines = quantities.map { (skuId, quantity) ->
            val sku = skus.getValue(skuId)
            val seller = sellers[sku.sellerId] ?: throw DomainException(ErrorCode.SKU_NOT_ORDERABLE)
            OrderLine(
                skuId = sku.skuId, productId = sku.productId, productName = sku.productName, brandName = sku.brandName,
                optionDisplayName = sku.optionDisplayName, salePrice = sku.salePrice, extraPrice = sku.extraPrice,
                sellerId = sku.sellerId, sellerName = seller.companyName, commissionRateBp = seller.commissionRateBp,
                quantity = quantity,
            )
        }
        val order = Order.create(
            orderNo = OrderNo.generate(now, random),
            memberId = memberId,
            lines = lines,
            allocation = DiscountAllocation.none(lines),
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
        paymentGateway.prepare(orderId, saved.orderNo, saved.totalAmount)
        return PlaceOrderResult(orderId, saved.orderNo, saved.totalAmount, saved.expiresAt)
    }

    /** 같은 SKU 는 수량을 합치고 SKU ID 오름차순으로 돌려준다. 항목이 없거나 수량이 1..999 밖이면 ORDER_INVALID. */
    private fun mergeQuantities(items: List<OrderItemCommand>): Map<Long, Int> {
        if (items.isEmpty() || items.any { it.quantity !in QUANTITY_RANGE }) throw DomainException(ErrorCode.ORDER_INVALID)
        val merged = items.groupBy { it.skuId }.mapValues { (_, group) -> group.sumOf { it.quantity } }
        if (merged.values.any { it !in QUANTITY_RANGE }) throw DomainException(ErrorCode.ORDER_INVALID)
        return merged.toSortedMap()
    }

    private fun ShippingAddressCommand.toDomain() = ShippingAddress(receiverName, phone, zipCode, address1, address2, memo)

    private companion object {
        val QUANTITY_RANGE = 1..999
    }
}
