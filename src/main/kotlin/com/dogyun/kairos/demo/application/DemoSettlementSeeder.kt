package com.dogyun.kairos.demo.application

import com.dogyun.kairos.benefit.application.CouponPolicyService
import com.dogyun.kairos.benefit.application.CouponService
import com.dogyun.kairos.benefit.application.CreateCouponPolicyCommand
import com.dogyun.kairos.catalog.application.ProductQueryService
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.member.application.MemberQueryService
import com.dogyun.kairos.order.application.ConfirmPaymentCommand
import com.dogyun.kairos.order.application.OrderItemCommand
import com.dogyun.kairos.order.application.OrderPaymentService
import com.dogyun.kairos.order.application.OrderQueryService
import com.dogyun.kairos.order.application.OrderReturnService
import com.dogyun.kairos.order.application.OrderService
import com.dogyun.kairos.order.application.OrderShippingService
import com.dogyun.kairos.order.application.PlaceOrderCommand
import com.dogyun.kairos.order.application.ProductCouponCommand
import com.dogyun.kairos.order.application.QuoteOrderCommand
import com.dogyun.kairos.order.application.RequestReturnCommand
import com.dogyun.kairos.order.application.ShippingAddressCommand
import com.dogyun.kairos.seller.application.SellerQueryService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

private val log = KotlinLogging.logger {}

/** [DemoSettlementSeeder.seedLastWeek] 의 결과. [sellerOrders] 는 이번에 만든 판매자 주문 수(이미 완성된 묶음이 있으면 0). */
data class SeedResult(val sellerOrders: Int)

/**
 * 정산 시연 데이터. 지난주(가장 최근에 끝난 월~일, KST)에 구매 확정된 데모 판매자 주문 3건을 만든다.
 * 금액을 직접 쓰지 않는다: 견적·주문·결제 승인(Mock PG)·발송·배송 완료·구매 확정·반품 요청/승인/수거 확인 애플리케이션 서비스를
 * 그대로 거치고, 시각만 [DemoShiftClock] 으로 지난주 화요일 10시부터 10분씩 앞으로 옮긴다(이 스레드에서만).
 *
 * - 주문 A: 50,000 + 30,000 (무료배송), 50,000 항목에 판매자 쿠폰 5,000 → 확정. 정산 라인 70,500
 * - 주문 B: 20,000 + 15,000 (배송비 3,000 구매자 부담), 20,000 항목 불량 반품 완료, 15,000 확정. 정산 라인 13,500
 * - 주문 C: 30,000 (배송비 3,000 구매자 부담), 불량 반품 완료 → 판매자 주문 자동 확정. 정산 라인 -3,000
 *
 * 멱등: **완성된 묶음** 기준이다. 지난주 안에 `confirmedAt` 이 있는 시연 구매자의 데모 판매자 주문이 3건 이상이면 건너뛴다.
 * `created_at`(JPA 감사 시각)은 실제 시각이라 판정에 쓰지 않는다. 중간에 멈춰 3건에 못 미친 묶음은 다음 실행(기동·`POST /demo/reseed`)에서
 * 새 묶음 3건으로 다시 만든다(멈춘 쪽에서 확정까지 간 주문이 있으면 정산에 한 줄 더 들어갈 수 있다 — 데모 데이터라 받아들인다).
 * 쿠폰 정책은 한 회원이 정책당 한 장만 받을 수 있으므로 주마다(실행마다) 새로 만든다(이름·유효기간이 그 주 월요일 기준).
 *
 * 스케줄러와의 경합: demo 에서는 스케줄 작업이 켜져 있고 그 스레드는 실제 시각을 본다. 만드는 중인 지난주 주문(결제 대기, 배송 완료·반품 요청 직후)을
 * 만료·자동 확정·반품 자동 승인 작업이 밀리초 단위의 창에서 건드릴 수 있다. 그러면 이 시더의 다음 단계가 실패할 수 있고,
 * 호출하는 쪽([com.dogyun.kairos.demo.infrastructure.DemoDataInitializer])이 예외를 로그만 남기고 기동을 계속한다. 다음 실행이 완성 판정으로 다시 만든다.
 *
 * `@Transactional` 을 두지 않는다 — 각 서비스가 자기 트랜잭션으로 처리한다.
 */
@Service
@Profile("demo")
class DemoSettlementSeeder(
    private val clock: DemoShiftClock,
    private val memberQueryService: MemberQueryService,
    private val sellerQueryService: SellerQueryService,
    private val productQueryService: ProductQueryService,
    private val couponPolicyService: CouponPolicyService,
    private val couponService: CouponService,
    private val orderService: OrderService,
    private val orderPaymentService: OrderPaymentService,
    private val orderShippingService: OrderShippingService,
    private val orderReturnService: OrderReturnService,
    private val orderQueryService: OrderQueryService,
) {

    @Synchronized
    fun seedLastWeek(): SeedResult {
        val buyerId = requireNotNull(memberQueryService.findIdByEmail(DemoAccounts.SETTLEMENT_BUYER)) { "정산 시연 구매자가 없습니다(DemoSeeder 를 먼저 실행)" }
        val sellerMemberId = requireNotNull(memberQueryService.findIdByEmail(DemoAccounts.SELLER)) { "데모 판매자가 없습니다" }
        val seller = requireNotNull(sellerQueryService.findSnapshotByMemberId(sellerMemberId)) { "데모 판매자 정보가 없습니다" }
        val sellerPrincipal = SellerPrincipal(seller.sellerId, seller.memberId, seller.canManage, seller.canView)

        // 오늘은 주입된 시계로 읽는다(시더를 이 스레드에서 옮긴 시계 아래에서 부르면 그 시각 기준의 "지난주").
        val today = LocalDate.now(clock)
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val weekStart = lastMonday.atStartOfDay()
        val weekEnd = lastMonday.plusWeeks(1).atStartOfDay()

        if (confirmedInWeek(buyerId, seller.sellerId, weekStart, weekEnd) >= BUNDLE_SIZE) {
            log.info { "지난주($lastMonday) 정산 시연 묶음이 이미 있어 건너뜁니다" }
            return SeedResult(0)
        }

        val skus = DEMO_SETTLEMENT_PRODUCTS.map { spec -> stockedOf(sellerPrincipal, spec.name) }
        val run = Run(buyerId, seller.sellerId, lastMonday)
        run.seed(skus)
        log.info { "지난주($lastMonday) 정산 시연 판매자 주문 $BUNDLE_SIZE 건을 만들었습니다" }
        return SeedResult(BUNDLE_SIZE)
    }

    private fun confirmedInWeek(buyerId: Long, sellerId: Long, from: LocalDateTime, to: LocalDateTime): Int {
        var count = 0
        orderQueryService.listMine(buyerId, 0, RECENT_ORDERS).content.forEach { summary ->
            count += orderQueryService.getMine(buyerId, summary.orderId).sellerOrders
                .count { so -> so.sellerId == sellerId && so.confirmedAt != null && !so.confirmedAt.isBefore(from) && so.confirmedAt.isBefore(to) }
        }
        return count
    }

    private fun stockedOf(seller: SellerPrincipal, productName: String): Stocked {
        val summary = productQueryService.listMine(seller, null, 0, PRODUCT_PAGE).content
            .firstOrNull { it.brandName == SETTLEMENT_DEMO_BRAND && it.name == productName && it.status == "ON_SALE" }
            ?: error("정산 시연 상품이 없습니다: $productName(DemoSeeder 를 먼저 실행)")
        return Stocked(summary.productId, productQueryService.getMine(seller, summary.productId).skus.first().skuId)
    }

    /** 한 번의 시더 실행. 단계마다 시각을 10분씩 앞으로 옮긴다. */
    private inner class Run(private val buyerId: Long, private val sellerId: Long, private val lastMonday: LocalDate) {
        private val startAt: Instant = lastMonday.plusDays(1).atTime(LocalTime.of(10, 0)).atZone(ZONE).toInstant()
        private var step = 0

        /** 이 단계의 시각(지난주 화요일 10:00 + 10분 × 단계)으로 이 스레드의 시계를 옮겨 [block] 을 실행한다. 실제 시각 기준으로 오프셋을 계산한다. */
        private fun <T> step(block: () -> T): T {
            val target = startAt.plus(Duration.ofMinutes(STEP_MINUTES * step++))
            return clock.withShift(Duration.between(Instant.now(), target), block)
        }

        fun seed(products: List<Stocked>) {
                        val (sku50, sku30a, sku20, sku15, sku30c) = products.map { it.skuId }
            val couponId = step {
                val policyId = couponPolicyService.createBySeller(sellerId, couponPolicy(products[0].productId))
                couponService.claim(buyerId, policyId).couponId
            }

            // 주문 A
            val a = placeAndPay(listOf(sku50 to 1, sku30a to 1), listOf(ProductCouponCommand(sku50, couponId)))
            ship(a)
            // 주문 B
            val b = placeAndPay(listOf(sku20 to 1, sku15 to 1))
            ship(b)
            // 주문 C
            val c = placeAndPay(listOf(sku30c to 1))
            ship(c)

            confirm(a)
            val bReturn = requestDefectiveReturn(b, sku20)
            completeReturn(bReturn)
            confirm(b)
            val cReturn = requestDefectiveReturn(c, sku30c)
            completeReturn(cReturn)
        }

        private fun placeAndPay(items: List<Pair<Long, Int>>, coupons: List<ProductCouponCommand> = emptyList()): Placed {
            val itemCommands = items.map { OrderItemCommand(it.first, it.second) }
            val placed = step {
                val quote = orderService.quote(buyerId, QuoteOrderCommand(itemCommands, coupons))
                orderService.place(buyerId, PlaceOrderCommand(itemCommands, quote.totalAmount, ADDRESS, coupons))
            }
            step { orderPaymentService.confirm(buyerId, ConfirmPaymentCommand(placed.orderNo, "demo-pay-${UUID.randomUUID()}", placed.totalAmount)) }
            val sellerOrderId = orderQueryService.getMine(buyerId, placed.orderId).sellerOrders.single().sellerOrderId
            return Placed(placed.orderId, sellerOrderId)
        }

        private fun ship(order: Placed) {
            step { orderShippingService.ship(sellerId, order.sellerOrderId, CARRIER_CODE, "DEMO${order.orderId}") }
            step { orderShippingService.deliver(sellerId, order.sellerOrderId) }
        }

        private fun confirm(order: Placed) {
            step { orderShippingService.confirmPurchase(buyerId, order.orderId, order.sellerOrderId) }
        }

        private fun requestDefectiveReturn(order: Placed, skuId: Long): Long = step {
            val item = orderQueryService.getMine(buyerId, order.orderId).sellerOrders.single().items.first { it.skuId == skuId }
            orderReturnService.request(buyerId, order.orderId, item.orderItemId, RequestReturnCommand("DEFECTIVE", null)).returnId
        }

        private fun completeReturn(returnId: Long) {
            step { orderReturnService.approveBySeller(sellerId, returnId) }
            step { orderReturnService.receiveBySeller(sellerId, returnId) }
        }

        /** 그 주 월요일 이름·유효기간의 판매자 상품 쿠폰 정책(정액 5,000원, 50,000원 상품 대상). 옮긴 시각 아래에서 만들어야 주문 시점에 유효하다. */
        private fun couponPolicy(targetProductId: Long) = CreateCouponPolicyCommand(
            name = "정산 시연 쿠폰 $lastMonday",
            couponType = "PRODUCT",
            targetType = "PRODUCT",
            targetProductId = targetProductId,
            discountType = "FIXED",
            discountValue = COUPON_DISCOUNT,
            maxDiscount = null,
            minOrderAmount = null,
            totalQuantity = null,
            validityDays = null,
            validFrom = lastMonday.atStartOfDay(),
            validUntil = lastMonday.plusWeeks(2).atStartOfDay(),
        )
    }

    private data class Stocked(val productId: Long, val skuId: Long)

    private data class Placed(val orderId: Long, val sellerOrderId: Long)

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        const val BUNDLE_SIZE = 3
        const val RECENT_ORDERS = 50
        const val PRODUCT_PAGE = 100
        const val STEP_MINUTES = 10L
        const val COUPON_DISCOUNT = 5_000L
        const val CARRIER_CODE = "04"
        val ADDRESS = ShippingAddressCommand("정산 시연", "010-0000-0000", "04524", "서울특별시 중구 세종대로 110", "시연동 1호", null)
    }
}
