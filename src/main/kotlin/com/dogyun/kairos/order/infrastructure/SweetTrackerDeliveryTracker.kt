package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.DeliveryTracker
import com.dogyun.kairos.order.application.TrackingResult
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

/**
 * 스마트택배(SweetTracker) 배송 조회. 비2xx·예외·타임아웃은 모두 [TrackingResult.UNAVAILABLE] 이다(예외를 던지지 않는다).
 * API 키가 URL 쿼리에 들어가므로 요청 URL 이나 예외 메시지는 로그에 남기지 않는다.
 */
class SweetTrackerDeliveryTracker(
    private val restClient: RestClient,
    private val properties: DeliveryProperties,
) : DeliveryTracker {

    override fun track(carrierCode: String, trackingNumber: String): TrackingResult = try {
        val sweet = properties.sweetTracker
        val body = restClient.get()
            .uri(
                "${sweet.baseUrl.trimEnd('/')}/api/v1/trackingInfo?t_key={key}&t_code={code}&t_invoice={invoice}",
                mapOf("key" to sweet.apiKey, "code" to carrierCode, "invoice" to trackingNumber),
            )
            .retrieve()
            .body(JsonNode::class.java)
        if (body != null && (body.path("complete").asBoolean(false) || body.path("level").asInt(0) == DELIVERED_LEVEL)) {
            TrackingResult.DELIVERED
        } else {
            TrackingResult.IN_TRANSIT
        }
    } catch (e: Exception) {
        log.warn("배송 조회 실패: carrier={} cause={}", carrierCode, e.javaClass.simpleName)
        TrackingResult.UNAVAILABLE
    }

    private companion object {
        const val DELIVERED_LEVEL = 6
        val log = LoggerFactory.getLogger(SweetTrackerDeliveryTracker::class.java)
    }
}
