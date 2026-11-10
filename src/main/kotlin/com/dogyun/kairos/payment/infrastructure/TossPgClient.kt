package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PgCancel
import com.dogyun.kairos.payment.application.PgClient
import com.dogyun.kairos.payment.application.PgPayment
import com.dogyun.kairos.payment.application.PgResult
import com.dogyun.kairos.payment.application.PgStatus
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

/**
 * 토스페이먼츠 PG. 4xx 는 토스 에러 본문(`code`, `message`)을 [PgResult.Failed] 로, 5xx·타임아웃·IO 예외는 [PgResult.Unknown] 으로 옮긴다.
 * 인증 헤더에 시크릿 키가 들어 있으므로 요청·예외 상세는 로그나 결과에 남기지 않고 예외 클래스 이름만 쓴다.
 */
class TossPgClient(private val restClient: RestClient) : PgClient {

    override fun confirm(paymentKey: String, pgOrderId: String, amount: Long): PgResult = execute("confirm") {
        restClient.post().uri("/v1/payments/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("paymentKey" to paymentKey, "orderId" to pgOrderId, "amount" to amount))
    }

    override fun cancel(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgResult =
        execute("cancel") {
            restClient.post().uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("cancelReason" to reason, "cancelAmount" to amount))
        }

    override fun find(paymentKey: String): PgResult = execute("find") {
        restClient.get().uri("/v1/payments/{paymentKey}", paymentKey)
    }

    private fun execute(operation: String, request: () -> RestClient.RequestHeadersSpec<*>): PgResult = try {
        request().exchange { _, response ->
            val status = response.statusCode
            val body = response.body.readAllBytes().toString(Charsets.UTF_8)
            when {
                status.is2xxSuccessful -> PgResult.Done(parsePayment(JSON.readTree(body)))
                status.is4xxClientError -> failed(operation, status.value(), body)
                else -> PgResult.Unknown("PG 오류 응답 HTTP ${status.value()}")
            }
        } ?: PgResult.Unknown("빈 응답")
    } catch (e: Exception) {
        log.warn("토스 {} 호출 실패: cause={}", operation, e.javaClass.simpleName)
        PgResult.Unknown(e.javaClass.simpleName)
    }

    private fun failed(operation: String, httpStatus: Int, body: String): PgResult {
        val error = runCatching { JSON.readTree(body) }.getOrNull()
        val code = error?.path("code")?.asString("").orEmpty()
        val message = error?.path("message")?.asString("").orEmpty()
        if (operation == "find" && (httpStatus == NOT_FOUND || code == "NOT_FOUND_PAYMENT")) {
            return PgResult.Failed("NOT_FOUND", message.ifBlank { "존재하지 않는 결제입니다." })
        }
        return PgResult.Failed(code.ifBlank { "HTTP_$httpStatus" }, message.ifBlank { "PG 요청이 거절되었습니다." })
    }

    private fun parsePayment(node: JsonNode) = PgPayment(
        paymentKey = node.path("paymentKey").asString(""),
        pgOrderId = node.path("orderId").asString(""),
        status = when (node.path("status").asString("")) {
            "DONE" -> PgStatus.DONE
            "CANCELED" -> PgStatus.CANCELED
            "PARTIAL_CANCELED" -> PgStatus.PARTIAL_CANCELED
            "ABORTED" -> PgStatus.ABORTED
            "EXPIRED" -> PgStatus.EXPIRED
            else -> PgStatus.OTHER
        },
        totalAmount = node.path("totalAmount").asLong(0),
        balanceAmount = node.path("balanceAmount").asLong(0),
        cancels = node.path("cancels").iterator().asSequence().map {
            PgCancel(it.path("cancelAmount").asLong(0), it.path("cancelReason").asString(""))
        }.toList(),
    )

    private companion object {
        const val NOT_FOUND = 404
        val log = LoggerFactory.getLogger(TossPgClient::class.java)
        val JSON = tools.jackson.databind.json.JsonMapper.builder().build()
    }
}
