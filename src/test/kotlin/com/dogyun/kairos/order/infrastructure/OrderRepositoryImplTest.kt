package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.ReturnReason
import com.dogyun.kairos.order.domain.ReturnStatus
import com.dogyun.kairos.order.domain.ADDRESS
import com.dogyun.kairos.order.domain.DiscountAllocation
import com.dogyun.kairos.order.domain.NOW
import com.dogyun.kairos.order.domain.POLICY
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrderStatus
import com.dogyun.kairos.order.domain.CouponIssuer
import com.dogyun.kairos.order.domain.line
import com.dogyun.kairos.order.domain.newOrderWith
import com.dogyun.kairos.order.domain.specAllocation
import com.dogyun.kairos.order.domain.specB1
import com.dogyun.kairos.order.domain.specC1
import com.dogyun.kairos.order.domain.newOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderRepositoryImplTest : CommerceTestSupport() {

    @Autowired
    private lateinit var repository: OrderRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private fun saveTwoSellerOrder() =
        repository.save(newOrder(line(sellerId = 2, price = 7_000, quantity = 2, skuId = 21), line(sellerId = 1, price = 9_000, skuId = 10), line(sellerId = 2, price = 5_000, skuId = 20)))

    @Test
    fun 저장_후_조회하면_판매자_주문과_항목이_ID_오름차순으로_복원된다() {
        val saved = saveTwoSellerOrder()

        val found = assertNotNull(repository.findById(requireNotNull(saved.id)))

        assertEquals(OrderStatus.PENDING_PAYMENT, found.status)
        assertEquals(saved.orderNo, found.orderNo)
        assertEquals(7L, found.memberId)
        assertEquals("홍길동", found.shippingAddress.receiverName)
        assertNull(found.shippingAddress.memo)
        assertEquals(found.sellerOrders.map { it.id }.sortedBy { it }, found.sellerOrders.map { it.id })
        assertEquals(listOf(1L, 2L), found.sellerOrders.map { it.sellerId })
        assertEquals(setOf(SellerOrderStatus.PENDING_PAYMENT), found.sellerOrders.map { it.status }.toSet())
        val items = found.allItems
        assertEquals(3, items.size)
        assertEquals(found.sellerOrders.flatMap { so -> so.items.map { it.id } }, items.map { it.id })
        found.sellerOrders.forEach { so -> assertEquals(so.items.map { it.id }.sortedBy { it }, so.items.map { it.id }) }
        assertEquals(listOf(21L, 20L), found.sellerOrders.last().items.map { it.skuId })
        assertEquals(saved.totalAmount, found.totalAmount)
        assertEquals(saved.expiresAt, found.expiresAt)
        assertEquals("상품10", items.first { it.skuId == 10L }.productName)

        assertEquals(found.orderNo, repository.findByOrderNo(found.orderNo)?.orderNo)
        assertEquals(found.id, repository.findBySellerOrderId(requireNotNull(found.sellerOrders.first().id))?.id)
    }

    @Test
    fun 할인_필드가_저장되고_복원된다() {
        val saved = repository.save(newOrderWith(specAllocation(), specB1(), specC1()))

        val found = assertNotNull(repository.findById(requireNotNull(saved.id)))

        assertEquals(6_000, found.productCouponDiscount)
        assertEquals(99L, found.cartCouponId)
        assertEquals(5_000, found.cartCouponDiscount)
        assertEquals(10_000, found.pointsUsed)
        assertEquals(0, found.pointsRefunded)
        assertEquals(49_000, found.totalAmount)
        val b = found.allItems.first { it.skuId == 21L }
        val c = found.allItems.first { it.skuId == 31L }
        assertEquals(1L, b.productCouponId)
        assertEquals(CouponIssuer.PLATFORM, b.productCouponIssuer)
        assertEquals(2_000, b.productCouponDiscount)
        assertEquals(2_187, b.cartCouponAllocated)
        assertEquals(4_375, b.pointsAllocated)
        assertEquals(21_438, b.paidAmount)
        assertEquals(CouponIssuer.SELLER, c.productCouponIssuer)
        assertEquals(27_562, c.paidAmount)

        found.markPaid(java.time.LocalDateTime.of(2026, 11, 10, 12, 0))
        val plan = found.planCancel(requireNotNull(c.id), com.dogyun.kairos.order.domain.CancelActor.BUYER, com.dogyun.kairos.order.domain.ShippingPolicy(50_000, 3_000))
        found.requestCancel(requireNotNull(c.id), com.dogyun.kairos.order.domain.CancelActor.BUYER, null, plan)
        found.confirmCancel(requireNotNull(c.id), plan, java.time.LocalDateTime.of(2026, 11, 10, 12, 1))
        repository.save(found)
        val after = assertNotNull(repository.findById(requireNotNull(saved.id)))
        assertEquals(5_625, after.pointsRefunded)
        assertEquals(5_625, after.allItems.first { it.skuId == 31L }.pointsRefunded)
        assertEquals(0, after.allItems.first { it.skuId == 21L }.pointsRefunded)
    }

    @Test
    fun 기존_주문을_고쳐_저장하면_상태가_갱신된다() {
        val saved = saveTwoSellerOrder()
        val loaded = requireNotNull(repository.findById(requireNotNull(saved.id)))
        loaded.markPaid(java.time.LocalDateTime.of(2026, 11, 10, 12, 5))

        repository.save(loaded)

        val found = requireNotNull(repository.findById(requireNotNull(saved.id)))
        assertEquals(OrderStatus.PAID, found.status)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 10, 12, 5), found.paidAt)
        assertEquals(setOf(SellerOrderStatus.PAID), found.sellerOrders.map { it.status }.toSet())
    }

    @Test
    fun findByIdForUpdate를_잡은_트랜잭션이_끝날_때까지_다른_트랜잭션의_findByIdForUpdate는_기다린다() {
        val orderId = requireNotNull(saveTwoSellerOrder().id)
        val locked = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val holder = pool.submit {
                transactionTemplate.executeWithoutResult {
                    repository.findByIdForUpdate(orderId)
                    locked.countDown()
                    Thread.sleep(HOLD_MILLIS)
                }
            }
            assertTrue(locked.await(10, TimeUnit.SECONDS))
            val waiter = pool.submit<Long> {
                val start = System.nanoTime()
                transactionTemplate.executeWithoutResult { repository.findByIdForUpdate(orderId) }
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            }

            val waitedMillis = waiter.get(20, TimeUnit.SECONDS)
            holder.get(20, TimeUnit.SECONDS)

            assertTrue(waitedMillis >= HOLD_MILLIS - 500, "락을 기다리지 않았다: ${waitedMillis}ms")
        } finally {
            pool.shutdownNow()
        }
    }

    /** 결제·배송 완료까지 진행해 저장한 단일 판매자 주문. */
    private fun savedDeliveredOrder(sku: Long, orderNo: String = "K20261110120000ABC123"): Order {
        val o = repository.save(
            Order.create(orderNo, 7L, listOf(line(sellerId = 1, price = 30_000, skuId = sku)), DiscountAllocation.none(listOf(line(sellerId = 1, price = 30_000, skuId = sku))), ADDRESS, POLICY, NOW, Duration.ofMinutes(15)),
        )
        o.markPaid(NOW)
        val soId = requireNotNull(o.sellerOrders.first().id)
        o.ship(soId, "CJ", "1", NOW)
        o.deliver(soId, NOW)
        return repository.save(o)
    }

    @Test
    fun 반품_요청이_항목과_함께_저장되고_복원된다() {
        val order = savedDeliveredOrder(10L)
        val itemId = requireNotNull(order.allItems.first().id)
        order.requestReturn(itemId, ReturnReason.DEFECTIVE, "불량", Duration.ofDays(7), NOW.plusDays(1))
        val saved = repository.save(order)

        val request = assertNotNull(saved.allItems.first().returnRequest)
        assertNotNull(request.id)
        assertEquals(ReturnStatus.REQUESTED, request.status)
        assertEquals(ReturnReason.DEFECTIVE, request.reason)
        assertEquals("불량", request.detail)
        assertEquals(itemId, request.orderItemId)
        assertEquals(saved.id, request.orderId)
        assertEquals(saved.allItems.first().status.name, "RETURN_REQUESTED")
        assertEquals(saved.id, repository.findOrderIdByReturnId(requireNotNull(request.id)))

        // 상태 변경은 같은 행을 갱신한다.
        saved.withdrawReturn(requireNotNull(request.id))
        val withdrawn = repository.save(saved).allItems.first().returnRequest!!
        assertEquals(request.id, withdrawn.id)
        assertEquals(ReturnStatus.WITHDRAWN, withdrawn.status)
        assertEquals(1, repository.findReturnPage(7L, null, null, 0, 10).totalElements)
        assertEquals(0, repository.findReturnPage(7L, null, ReturnStatus.REQUESTED, 0, 10).totalElements)
    }

    @Test
    fun findReturnIdsRequestedBefore는_REQUESTED만_id_keyset으로_준다() {
        val ids = (1..3).map { i ->
            val order = savedDeliveredOrder(10L * i, "K2026111012000${i}ABC123")
            order.requestReturn(requireNotNull(order.allItems.first().id), ReturnReason.CHANGE_OF_MIND, null, Duration.ofDays(7), NOW.plusDays(1))
            repository.save(order)
        }
        val returnIds = ids.map { requireNotNull(it.allItems.first().returnRequest?.id) }
        // 두 번째는 철회해 REQUESTED 에서 빠진다.
        val second = requireNotNull(repository.findById(requireNotNull(ids[1].id)))
        second.withdrawReturn(returnIds[1])
        repository.save(second)

        val threshold = NOW.plusDays(2)
        val all = repository.findReturnIdsRequestedBefore(threshold, 0, 10)
        assertEquals(listOf(returnIds[0], returnIds[2]), all.map { it.second })
        assertEquals(listOf(ids[0].id, ids[2].id), all.map { it.first })
        assertEquals(listOf(returnIds[2]), repository.findReturnIdsRequestedBefore(threshold, returnIds[0], 10).map { it.second })
        assertEquals(listOf(returnIds[0]), repository.findReturnIdsRequestedBefore(threshold, 0, 1).map { it.second })
        assertTrue(repository.findReturnIdsRequestedBefore(NOW, 0, 10).isEmpty())
    }

    private companion object {
        const val HOLD_MILLIS = 1_500L
    }
}
