package com.dogyun.kairos.settlement.domain

import java.time.LocalDateTime

/** 정산 라인 = 판매자 주문 1개의 스냅샷. 불변. */
class SettlementLine(
    val id: Long?,
    val sellerOrderId: Long,
    val orderId: Long,
    val orderNo: String,
    val confirmedAt: LocalDateTime,
    val calc: LineCalculation,
)
