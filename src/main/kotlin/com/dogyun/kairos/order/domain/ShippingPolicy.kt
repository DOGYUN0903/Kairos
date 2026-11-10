package com.dogyun.kairos.order.domain

/** 무료배송 기준 금액과 기본 배송비(원). 설정값 `kairos.order.*`에서 온다. */
data class ShippingPolicy(val freeShippingThreshold: Long, val baseShippingFee: Long)
