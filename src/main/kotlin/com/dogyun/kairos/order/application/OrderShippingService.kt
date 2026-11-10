package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrder
import com.dogyun.kairos.order.domain.SellerOrderStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

data class SellerOrderState(val sellerOrderId: Long, val sellerOrderStatus: SellerOrderStatus, val orderStatus: OrderStatus)

/**
 * 배송 시작·완료·구매 확정. 모든 메서드는 주문 행을 먼저 잠근다(항목 취소와 같은 락이라 취소와 배송 시작이 겹쳐도 하나만 이긴다).
 * 판매자 주문 기준 메서드는 주문 id 를 트랜잭션 밖에서 찾은 뒤 락을 잡는다(스냅샷이 락보다 먼저 만들어지지 않게). 이 클래스에는 `@Transactional` 을 붙이지 않는다.
 * 이미 목표 상태면 그대로 성공(PATCH 멱등)이다. 택배사 조회는 여기서 하지 않는다(트랜잭션 밖에서 [DeliveryTracker] 를 호출한다).
 */
@Service
class OrderShippingService(
    private val orderRepository: OrderRepository,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {

    fun ship(sellerId: Long, sellerOrderId: Long, carrierCode: String, trackingNumber: String): SellerOrderState =
        forSeller(sellerId, sellerOrderId) { order ->
            order.ship(sellerOrderId, carrierCode, trackingNumber, now())
        }

    fun deliver(sellerId: Long, sellerOrderId: Long): SellerOrderState =
        forSeller(sellerId, sellerOrderId) { order -> order.deliver(sellerOrderId, now()) }

    /** 택배사 조회로 배송 완료를 확인했을 때. 판매자 주문이 `SHIPPING` 이 아니면 아무것도 하지 않는다. */
    fun deliverBySystem(sellerOrderId: Long) {
        val orderId = orderRepository.findOrderIdBySellerOrderId(sellerOrderId) ?: return
        tx.run {
            val order = orderRepository.findByIdForUpdate(orderId) ?: return@run
            if (order.findSellerOrder(sellerOrderId).status != SellerOrderStatus.SHIPPING) return@run
            order.deliver(sellerOrderId, now())
            orderRepository.save(order)
        }
    }

    fun confirmPurchase(memberId: Long, orderId: Long, sellerOrderId: Long): SellerOrderState = tx.run {
        val order = orderRepository.findByIdForUpdate(orderId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        val sellerOrder = order.findSellerOrder(sellerOrderId)
        order.confirmPurchase(sellerOrderId, now())
        save(order, sellerOrder)
    }

    /** 배송 완료 후 정해진 기간이 지나 자동으로 확정할 때. 판매자 주문이 `DELIVERED` 가 아니면 아무것도 하지 않는다. */
    fun autoConfirm(sellerOrderId: Long) {
        val orderId = orderRepository.findOrderIdBySellerOrderId(sellerOrderId) ?: return
        tx.run {
            val order = orderRepository.findByIdForUpdate(orderId) ?: return@run
            if (order.findSellerOrder(sellerOrderId).status != SellerOrderStatus.DELIVERED) return@run
            order.confirmPurchase(sellerOrderId, now())
            orderRepository.save(order)
        }
    }

    private fun forSeller(sellerId: Long, sellerOrderId: Long, change: (Order) -> Unit): SellerOrderState {
        val orderId = orderRepository.findOrderIdBySellerOrderId(sellerOrderId)
            ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
        return tx.run {
            val order = orderRepository.findByIdForUpdate(orderId) ?: throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
            val sellerOrder = order.findSellerOrder(sellerOrderId)
            // 결제 전 판매자 주문은 판매자에게 없는 것으로 친다(조회·취소 API 와 같다).
            if (sellerOrder.sellerId != sellerId || sellerOrder.status == SellerOrderStatus.PENDING_PAYMENT) {
                throw DomainException(ErrorCode.SELLER_ORDER_NOT_FOUND)
            }
            change(order)
            save(order, sellerOrder)
        }
    }

    private fun save(order: Order, sellerOrder: SellerOrder): SellerOrderState {
        val saved = orderRepository.save(order)
        return SellerOrderState(requireNotNull(sellerOrder.id), sellerOrder.status, saved.status)
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)
}
