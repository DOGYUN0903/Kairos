package com.dogyun.kairos.order.application

enum class TrackingResult { DELIVERED, IN_TRANSIT, UNAVAILABLE }

/** 택배사 배송 조회. 구현은 예외를 던지지 않고 조회할 수 없으면 [TrackingResult.UNAVAILABLE] 을 돌려준다. DB 트랜잭션 밖에서 호출한다. */
interface DeliveryTracker {
    fun track(carrierCode: String, trackingNumber: String): TrackingResult
}
