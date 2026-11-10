package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.DeliveryTracker
import com.dogyun.kairos.order.application.TrackingResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** 로컬·테스트용. 송장 번호가 `-D` 로 끝나면 배송 완료, 아니면 배송 중이다. */
@Component
@ConditionalOnProperty("kairos.delivery.provider", havingValue = "mock", matchIfMissing = true)
class MockDeliveryTracker : DeliveryTracker {
    override fun track(carrierCode: String, trackingNumber: String): TrackingResult =
        if (trackingNumber.endsWith("-D")) TrackingResult.DELIVERED else TrackingResult.IN_TRANSIT
}
