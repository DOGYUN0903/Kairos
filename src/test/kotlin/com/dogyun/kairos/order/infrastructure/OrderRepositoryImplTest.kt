package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import com.dogyun.kairos.order.domain.SellerOrderStatus
import com.dogyun.kairos.order.domain.line
import com.dogyun.kairos.order.domain.newOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    private companion object {
        const val HOLD_MILLIS = 1_500L
    }
}
