package com.dogyun.kairos.order.application

import com.dogyun.kairos.order.domain.OrderItemStatus
import com.dogyun.kairos.order.domain.OrderStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentReconciliationServiceTest {

    private val f = OrderFlowFixture()
    private val service = PaymentReconciliationService(
        f.orderRepository, f.paymentGateway, f.paymentService, f.cancelService, f.properties, f.clock,
    )

    @Test
    fun UNKNOWN_결제가_PG에서_DONE이면_주문이_PAID가_된다() {
        val order = f.place()
        f.startPayment(order, "unknown-1", unknown = true)
        f.pgDone("unknown-1")

        assertEquals(1, service.reconcileApprovals())

        assertEquals(OrderStatus.PAID, f.reload(order).status)
        assertEquals("APPROVED", f.paymentGateway.get(order.id!!).status)
        assertEquals(1, f.stockManager.sales[110L])
    }

    @Test
    fun UNKNOWN_결제가_PG에_없거나_ABORTED_EXPIRED_CANCELED면_주문_CANCELED_재고_복구() {
        val notFound = f.place().also { f.startPayment(it, "lost-1", unknown = true) }
        val others = listOf(PgPaymentStatus.ABORTED, PgPaymentStatus.EXPIRED, PgPaymentStatus.CANCELED).mapIndexed { i, status ->
            f.place().also { f.startPayment(it, "unknown-$i", unknown = true); f.pgDone("unknown-$i", status, balance = 0) }
        }

        assertEquals(4, service.reconcileApprovals())

        (listOf(notFound) + others).forEach {
            assertEquals(OrderStatus.CANCELED, f.reload(it).status)
            assertEquals("FAILED", f.paymentGateway.get(it.id!!).status)
        }
        assertEquals(10, f.stockManager.stock[10L])
        assertEquals(10, f.stockManager.stock[20L])
    }

    @Test
    fun PG_금액이_다르면_void_후_거절한다() {
        val order = f.place()
        f.startPayment(order, "unknown-1", unknown = true)
        f.pgDone("unknown-1", total = 50_000)
        f.pgCancelDone(50_000)

        assertEquals(1, service.reconcileApprovals())

        assertEquals(listOf(CancelCall("unknown-1", 50_000, "amount-mismatch:${order.id}", "void-${order.id}-50000")), f.paymentGateway.cancelCalls)
        assertEquals(OrderStatus.CANCELED, f.reload(order).status)
        assertEquals("FAILED", f.paymentGateway.get(order.id!!).status)
        assertEquals(10, f.stockManager.stock[10L])
    }

    @Test
    fun OTHER_상태나_Unknown은_건너뛴다() {
        val other = f.place().also { f.startPayment(it, "unknown-1", unknown = true); f.pgDone("unknown-1", PgPaymentStatus.OTHER) }
        val unknown = f.place().also { f.startPayment(it, "unknown-2", unknown = true); f.paymentGateway.pgPayments["unknown-2"] = PgOutcome.Unknown }

        assertEquals(0, service.reconcileApprovals())

        listOf(other, unknown).forEach {
            assertEquals(OrderStatus.PENDING_PAYMENT, f.reload(it).status)
            assertEquals("UNKNOWN", f.paymentGateway.get(it.id!!).status)
        }
        assertTrue(f.paymentGateway.cancelCalls.isEmpty())
    }

    @Test
    fun 이미_만료_취소된_주문의_결제가_PG에서_DONE이면_전액_취소하고_결제는_CANCELED_전액_환불이다() {
        val order = f.place()
        f.startPayment(order, "unknown-1", unknown = true)
        // 승인 확인이 늦어지는 사이 주문이 닫혔다.
        f.orderRepository.save(f.reload(order).also { it.expire() })
        f.pgDone("unknown-1")
        f.pgCancelDone(60_000)

        assertEquals(1, service.reconcileApprovals())

        assertEquals(listOf(CancelCall("unknown-1", 60_000, "order-closed:${order.id}", "void-${order.id}-60000")), f.paymentGateway.cancelCalls)
        val payment = f.paymentGateway.get(order.id!!)
        assertEquals("CANCELED", payment.status)
        assertEquals(60_000, payment.refundedAmount)
    }

    @Test
    fun void가_유실되어_PG_잔액이_남은_닫힌_주문은_다시_전액_취소하고_잔액이_없는_결제는_닫는다() {
        // 후보는 결제 쪽에서 고른다: payment_key 가 있고 FAILED/UNKNOWN 이며 주문이 CANCELED 인 것.
        val failed = f.place().also { f.startPayment(it, "pay-1"); f.paymentGateway.fail(it.id!!, "amount-mismatch") }
        val unknown = f.place().also { f.startPayment(it, "pay-2", unknown = true) }
        val settled = f.place().also { f.startPayment(it, "pay-3", unknown = true); f.paymentGateway.voidAfterApproval(it.id!!) }
        val clean = f.place().also { f.startPayment(it, "pay-4"); f.paymentGateway.fail(it.id!!, "x") }
        listOf(failed, unknown, settled, clean).forEach { o -> f.orderRepository.save(f.reload(o).also { it.expire() }) }
        f.pgDone("pay-1"); f.pgDone("pay-2"); f.pgDone("pay-3"); f.pgDone("pay-4", PgPaymentStatus.CANCELED, balance = 0)
        f.pgCancelDone(60_000)

        assertEquals(2, service.revoidClosedOrders())

        assertEquals(
            listOf("pay-1" to failed, "pay-2" to unknown).map { (key, o) ->
                CancelCall(key, 60_000, "order-closed:${o.id}", "void-${o.id}-60000")
            },
            f.paymentGateway.cancelCalls,
        )
        assertEquals("CANCELED", f.paymentGateway.get(unknown.id!!).status)
        assertEquals("FAILED", f.paymentGateway.get(failed.id!!).status)
        // 잔액이 없다고 PG 가 확정한 결제는 닫혀 더는 후보가 아니다.
        assertEquals("CANCELED", f.paymentGateway.get(clean.id!!).status)
        assertEquals(0, f.paymentGateway.get(clean.id!!).refundedAmount)

        // 다음 주기: PG 가 이제 잔액 0 이라고 답하면 FAILED 결제도 닫힌다.
        f.pgDone("pay-1", PgPaymentStatus.CANCELED, balance = 0)
        f.paymentGateway.cancelCalls.clear()
        assertEquals(0, service.revoidClosedOrders())
        assertEquals("CANCELED", f.paymentGateway.get(failed.id!!).status)
        assertTrue(f.paymentGateway.cancelCalls.isEmpty())
        assertTrue(f.paymentGateway.findVoidCandidates(0, 100).isEmpty())
    }

    @Test
    fun 주문이_닫히지_않았거나_PG_조회를_확정하지_못하면_후보로_남는다() {
        val pending = f.place().also { f.startPayment(it, "pay-1", unknown = true) } // 주문은 아직 PENDING_PAYMENT
        val lost = f.place().also { f.startPayment(it, "lost-2"); f.paymentGateway.fail(it.id!!, "x") }
        val transient = f.place().also { f.startPayment(it, "pay-3"); f.paymentGateway.fail(it.id!!, "x") }
        val other = f.place().also { f.startPayment(it, "pay-4"); f.paymentGateway.fail(it.id!!, "x") }
        listOf(lost, transient, other).forEach { o -> f.orderRepository.save(f.reload(o).also { it.expire() }) }
        f.paymentGateway.pgPayments["pay-3"] = PgOutcome.Failed("PROVIDER_ERROR", "일시 오류")
        f.pgDone("pay-4", PgPaymentStatus.OTHER)
        // lost-2 는 PG 에 없다(NOT_FOUND): 확정적으로 잔액 없음.

        assertEquals(0, service.revoidClosedOrders())

        assertEquals("UNKNOWN", f.paymentGateway.get(pending.id!!).status)
        assertEquals("CANCELED", f.paymentGateway.get(lost.id!!).status)
        assertEquals("FAILED", f.paymentGateway.get(transient.id!!).status)
        assertEquals("FAILED", f.paymentGateway.get(other.id!!).status)
        assertTrue(f.paymentGateway.cancelCalls.isEmpty())
    }

    @Test
    fun 재취소_후보가_한_페이지를_넘어도_뒤의_닫힌_주문까지_처리한다() {
        // 앞의 후보 3건은 PG 조회가 계속 일시 오류라 집합에 남는다. 페이지 크기 2 로도 뒤의 4번째까지 닿아야 한다.
        val stuck = (1..3).map { i ->
            f.place().also { f.startPayment(it, "stuck-$i"); f.paymentGateway.fail(it.id!!, "x"); f.orderRepository.save(f.reload(it).also { o -> o.expire() }) }
                .also { f.paymentGateway.pgPayments["stuck-$i"] = PgOutcome.Failed("PROVIDER_ERROR", "일시 오류") }
        }
        val last = f.place().also { f.startPayment(it, "pay-last"); f.paymentGateway.fail(it.id!!, "x"); f.orderRepository.save(f.reload(it).also { o -> o.expire() }) }
        f.pgDone("pay-last")
        f.pgCancelDone(60_000)

        assertEquals(1, service.revoidClosedOrders(limit = 2))

        assertEquals(listOf("pay-last"), f.paymentGateway.cancelCalls.map { it.paymentKey })
        stuck.forEach { assertEquals("FAILED", f.paymentGateway.get(it.id!!).status) }
        assertEquals(last.id, f.paymentGateway.cancelCalls.single().reason.substringAfter(':').toLong())
    }

    @Test
    fun CANCEL_REQUESTED_항목이_한_페이지를_넘어도_뒤의_항목까지_대사한다() {
        // PG 조회가 일시 오류인 앞의 항목은 CANCEL_REQUESTED 로 남지만 뒤의 항목을 막지 않는다.
        val (stuckOrder, _, _) = cancelRequested()
        f.paymentGateway.pgPayments["pay-${stuckOrder.id}"] = PgOutcome.Failed("PROVIDER_ERROR", "일시 오류")
        val (order, itemId, refund) = cancelRequested()
        f.pgDone("pay-${order.id}", PgPaymentStatus.PARTIAL_CANCELED, balance = 60_000 - refund, cancels = listOf(PgCancelInfo(refund, "orderItem:$itemId")))

        assertEquals(1, service.reconcileCancels(limit = 1))

        assertEquals(OrderItemStatus.CANCELED, f.reload(order).findItem(itemId).status)
    }

    @Test
    fun 정체된_IN_PROGRESS도_대상이다() {
        val order = f.place()
        f.startPayment(order, "pay-1")
        f.pgDone("pay-1")

        assertEquals(1, service.reconcileApprovals())

        assertEquals(OrderStatus.PAID, f.reload(order).status)
    }

    private fun cancelRequested(): Triple<com.dogyun.kairos.order.domain.Order, Long, Long> {
        val order = f.paid()
        val item = order.allItems.first { it.skuId == 10L }
        f.paymentGateway.nextCancel = PgOutcome.Unknown
        f.cancelService.cancelByBuyer(f.memberId, order.id!!, item.id!!, "변심")
        val requested = f.reload(order).findItem(item.id!!)
        assertEquals(OrderItemStatus.CANCEL_REQUESTED, requested.status)
        f.paymentGateway.cancelCalls.clear()
        return Triple(order, item.id!!, requested.refundAmount)
    }

    @Test
    fun CANCEL_REQUESTED_항목이_PG_cancels에_있으면_CANCELED로_확정한다() {
        val (order, itemId, refund) = cancelRequested()
        f.pgDone("pay-${order.id}", PgPaymentStatus.PARTIAL_CANCELED, balance = 60_000 - refund, cancels = listOf(PgCancelInfo(refund, "orderItem:$itemId")))

        assertEquals(1, service.reconcileCancels())

        assertEquals(OrderItemStatus.CANCELED, f.reload(order).findItem(itemId).status)
        assertEquals(refund, f.paymentGateway.get(order.id!!).refundedAmount)
        assertEquals(10, f.stockManager.stock[10L])
        assertTrue(f.paymentGateway.cancelCalls.isEmpty())
    }

    @Test
    fun PG에_없으면_같은_멱등_키로_취소를_다시_호출한다() {
        val (order, itemId, refund) = cancelRequested()
        f.pgDone("pay-${order.id}")
        f.pgCancelDone(refund, balance = 60_000 - refund)

        assertEquals(1, service.reconcileCancels())

        assertEquals(listOf(CancelCall("pay-${order.id}", refund, "orderItem:$itemId", "refund-$itemId-$refund")), f.paymentGateway.cancelCalls)
        assertEquals(OrderItemStatus.CANCELED, f.reload(order).findItem(itemId).status)
    }

    @Test
    fun PG_조회가_NOT_FOUND가_아닌_Failed면_취소를_다시_부르지_않고_건너뛴다() {
        val (order, itemId, _) = cancelRequested()
        f.paymentGateway.pgPayments["pay-${order.id}"] = PgOutcome.Failed("PROVIDER_ERROR", "일시 오류")

        assertEquals(0, service.reconcileCancels())

        assertTrue(f.paymentGateway.cancelCalls.isEmpty())
        assertEquals(OrderItemStatus.CANCEL_REQUESTED, f.reload(order).findItem(itemId).status)
    }
}
