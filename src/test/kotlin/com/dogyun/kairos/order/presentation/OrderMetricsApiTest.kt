package com.dogyun.kairos.order.presentation

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.test.web.servlet.post
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * 비즈니스 메트릭 통합 테스트. 관리 포트를 열어 실제 Prometheus 스크레이프 포맷으로도 확인한다.
 * 레지스트리는 컨텍스트 전체에서 공유되므로 카운터는 전후 차이로 검증한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.server.port=0"],
)
@AutoConfigureMetrics
class OrderMetricsApiTest : OrderApiTestSupport() {

    @Autowired
    private lateinit var registry: MeterRegistry

    @LocalManagementPort
    private var managementPort: Int = 0

    private fun placed(result: String): Double =
        registry.find("kairos.order.placed").tag("result", result).counter()?.count() ?: 0.0

    private fun gauge(name: String, dealId: Long): Double? =
        registry.find(name).tag("dealId", dealId.toString()).gauge()?.value()

    private fun scrape(): String = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:$managementPort/actuator/prometheus")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    ).body()

    private fun cancel(token: String, orderId: Long) = mockMvc.post("/api/v1/orders/$orderId/cancel") {
        header("Authorization", "Bearer $token")
    }

    @Test
    fun 주문하면_prometheus에_결과와_순판매량이_보인다() {
        val dealId = createOngoingDeal(totalQuantity = 10)
        val before = placed("PAID")

        order(userToken(), dealId, 2).andExpect { status { isCreated() } }

        assertEquals(before + 1, placed("PAID"))
        assertEquals(10.0, gauge("kairos.deal.capacity", dealId))
        assertEquals(2.0, gauge("kairos.deal.sold", dealId))
        assertEquals(0.0, gauge("kairos.deal.oversold", dealId))

        val body = scrape()
        assertContains(body, "kairos_order_placed_total{result=\"PAID\"}")
        assertContains(body, "kairos_deal_sold{dealId=\"$dealId\"} 2.0")
        assertContains(body, "kairos_deal_capacity{dealId=\"$dealId\"} 10.0")
        assertContains(body, "kairos_deal_oversold{dealId=\"$dealId\"} 0.0")
        assertContains(body, "kairos_payment_duration_seconds_bucket{")
    }

    @Test
    fun 롤백된_주문은_순판매량에_반영되지_않는다() {
        val dealId = createOngoingDeal(totalQuantity = 1, perMemberLimit = 5)
        val before = placed("SOLD_OUT")

        order(userToken(), dealId, 2).andExpect { status { isConflict() } }

        assertEquals(before + 1, placed("SOLD_OUT"))
        assertEquals(null, gauge("kairos.deal.sold", dealId))
        assertEquals(1, remaining(dealId))
    }

    @Test
    fun 취소하면_순판매량이_줄어든다() {
        val dealId = createOngoingDeal(totalQuantity = 10)
        val token = userToken()
        val orderId = json(order(token, dealId, 2).andReturn()).get("data").get("orderId").asLong()
        assertEquals(2.0, gauge("kairos.deal.sold", dealId))

        cancel(token, orderId).andExpect { status { isOk() } }

        assertEquals(0.0, gauge("kairos.deal.sold", dealId))
        assertEquals(0.0, gauge("kairos.deal.oversold", dealId))
    }
}
