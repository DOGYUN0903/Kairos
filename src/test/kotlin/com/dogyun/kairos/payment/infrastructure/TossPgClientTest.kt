package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PgCancel
import com.dogyun.kairos.payment.application.PgResult
import com.dogyun.kairos.payment.application.PgStatus
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse

class TossPgClientTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val properties = PgProperties.Toss(baseUrl = "https://toss.test", secretKey = "test_sk_SECRET")
    private val client = TossPgClient(TossPgConfig.restClient(builder, properties))

    private fun paymentJson(status: String, balance: Long, cancels: String = "[]") =
        """{"paymentKey":"pk-1","orderId":"ORD-1","status":"$status","totalAmount":10000,"balanceAmount":$balance,"cancels":$cancels}"""

    @Test
    fun 승인_요청은_Basic_인증과_paymentKey_orderId_amount를_보내고_DONE을_Done으로_매핑한다() {
        val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("test_sk_SECRET:".toByteArray())
        server.expect(requestTo("https://toss.test/v1/payments/confirm"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", expectedAuth))
            .andExpect(jsonPath("$.paymentKey").value("pk-1"))
            .andExpect(jsonPath("$.orderId").value("ORD-1"))
            .andExpect(jsonPath("$.amount").value(10000))
            .andRespond(withSuccess(paymentJson("DONE", 10000), MediaType.APPLICATION_JSON))

        val result = client.confirm("pk-1", "ORD-1", 10000)

        val done = assertIs<PgResult.Done>(result)
        assertEquals(PgStatus.DONE, done.payment.status)
        assertEquals("pk-1", done.payment.paymentKey)
        assertEquals("ORD-1", done.payment.pgOrderId)
        assertEquals(10000L, done.payment.totalAmount)
        assertEquals(10000L, done.payment.balanceAmount)
        server.verify()
    }

    @Test
    fun 알_수_없는_status는_OTHER로_매핑한다() {
        server.expect(method(HttpMethod.POST))
            .andRespond(withSuccess(paymentJson("IN_PROGRESS", 10000), MediaType.APPLICATION_JSON))
        assertEquals(PgStatus.OTHER, assertIs<PgResult.Done>(client.confirm("pk-1", "ORD-1", 10000)).payment.status)
    }

    @Test
    fun `4xx 에러 본문은 Failed code message다`() {
        server.expect(method(HttpMethod.POST)).andRespond(
            withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("""{"code":"REJECT_CARD_COMPANY","message":"카드사 거절"}"""),
        )
        assertEquals(PgResult.Failed("REJECT_CARD_COMPANY", "카드사 거절"), client.confirm("pk-1", "ORD-1", 10000))
    }

    private fun confirmError(code: String) = server.expect(method(HttpMethod.POST)).andRespond(
        withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
            .body("""{"code":"$code","message":"m"}"""),
    )

    @Test
    fun `승인이 400 ALREADY_PROCESSED_PAYMENT면 Unknown이다`() {
        confirmError("ALREADY_PROCESSED_PAYMENT")
        assertIs<PgResult.Unknown>(client.confirm("pk-1", "ORD-1", 10000))
    }

    @Test
    fun `승인이 일시 오류 코드면 Unknown이다`() {
        confirmError("FAILED_INTERNAL_SYSTEM_PROCESSING")
        assertIs<PgResult.Unknown>(client.confirm("pk-1", "ORD-1", 10000))
        server.reset()
        confirmError("PROVIDER_ERROR")
        assertIs<PgResult.Unknown>(client.confirm("pk-1", "ORD-1", 10000))
    }

    @Test
    fun `승인이 일반 거절 코드면 여전히 Failed다`() {
        confirmError("REJECT_CARD_PAYMENT")
        assertEquals(PgResult.Failed("REJECT_CARD_PAYMENT", "m"), client.confirm("pk-1", "ORD-1", 10000))
    }

    @Test
    fun `5xx는 Unknown이다`() {
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
        assertIs<PgResult.Unknown>(client.confirm("pk-1", "ORD-1", 10000))
    }

    @Test
    fun `IO 예외는 Unknown이고 비밀키를 담지 않는다`() {
        server.expect(method(HttpMethod.POST)).andRespond { throw IOException("connect timed out test_sk_SECRET") }
        val unknown = assertIs<PgResult.Unknown>(client.confirm("pk-1", "ORD-1", 10000))
        assertFalse(unknown.cause.contains("test_sk_SECRET"))
    }

    @Test
    fun 취소_요청에_Idempotency_Key_헤더와_cancelAmount가_들어가고_cancels가_매핑된다() {
        server.expect(requestTo("https://toss.test/v1/payments/pk-1/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key", "idem-1"))
            .andExpect(jsonPath("$.cancelReason").value("단순 변심"))
            .andExpect(jsonPath("$.cancelAmount").value(3000))
            .andRespond(
                withSuccess(
                    paymentJson("PARTIAL_CANCELED", 7000, """[{"cancelAmount":3000,"cancelReason":"단순 변심"}]"""),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val done = assertIs<PgResult.Done>(client.cancel("pk-1", 3000, "단순 변심", "idem-1"))

        assertEquals(PgStatus.PARTIAL_CANCELED, done.payment.status)
        assertEquals(7000L, done.payment.balanceAmount)
        assertEquals(listOf(PgCancel(3000, "단순 변심")), done.payment.cancels)
        server.verify()
    }

    @Test
    fun 조회_404는_Failed_NOT_FOUND다() {
        server.expect(requestTo("https://toss.test/v1/payments/pk-x"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                    .body("""{"code":"NOT_FOUND_PAYMENT","message":"존재하지 않는 결제 정보 입니다."}"""),
            )
        val failed = assertIs<PgResult.Failed>(client.find("pk-x"))
        assertEquals("NOT_FOUND", failed.code)
    }

    @Test
    fun 조회_성공은_Done이다() {
        server.expect(requestTo("https://toss.test/v1/payments/pk-1"))
            .andRespond(withSuccess(paymentJson("CANCELED", 0), MediaType.APPLICATION_JSON))
        assertEquals(PgStatus.CANCELED, assertIs<PgResult.Done>(client.find("pk-1")).payment.status)
    }
}
