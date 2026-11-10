package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.TrackingResult
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals

class SweetTrackerDeliveryTrackerTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val properties = DeliveryProperties(
        provider = "sweettracker",
        sweetTracker = DeliveryProperties.SweetTracker(baseUrl = "https://tracker.test", apiKey = "KEY123"),
    )
    private val tracker = SweetTrackerDeliveryTracker(builder.build(), properties)

    private fun respond(body: String) =
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

    @Test
    fun complete_true면_DELIVERED() {
        respond("""{"complete": true, "level": 6}""")
        assertEquals(TrackingResult.DELIVERED, tracker.track("04", "123456"))
    }

    @Test
    fun level_6이면_DELIVERED() {
        respond("""{"complete": false, "level": 6}""")
        assertEquals(TrackingResult.DELIVERED, tracker.track("04", "123456"))
    }

    @Test
    fun level_4면_IN_TRANSIT() {
        respond("""{"complete": false, "level": 4}""")
        assertEquals(TrackingResult.IN_TRANSIT, tracker.track("04", "123456"))
    }

    @Test
    fun `500이면_UNAVAILABLE`() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
        assertEquals(TrackingResult.UNAVAILABLE, tracker.track("04", "123456"))
    }

    @Test
    fun 응답_본문이_깨져도_UNAVAILABLE이고_예외를_던지지_않는다() {
        respond("not-json")
        assertEquals(TrackingResult.UNAVAILABLE, tracker.track("04", "123456"))
    }

    @Test
    fun 요청에_t_key_t_code_t_invoice가_들어간다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://tracker.test/api/v1/trackingInfo")))
            .andExpect(queryParam("t_key", "KEY123"))
            .andExpect(queryParam("t_code", "04"))
            .andExpect(queryParam("t_invoice", "123456"))
            .andRespond(withSuccess("""{"complete": false, "level": 2}""", MediaType.APPLICATION_JSON))
        tracker.track("04", "123456")
        server.verify()
    }
}
