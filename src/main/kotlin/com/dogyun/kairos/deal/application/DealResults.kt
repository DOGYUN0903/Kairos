package com.dogyun.kairos.deal.application

import com.dogyun.kairos.deal.domain.DealStatus
import java.time.LocalDateTime

/** 다른 모듈(order)이 사용하는 딜 스냅샷. 도메인 타입을 노출하지 않는다. */
data class DealSnapshot(
    val dealId: Long,
    val dealPrice: Long,
    val perMemberLimit: Int,
    val ongoing: Boolean,
    val totalQuantity: Int,
)

data class DealSummary(
    val dealId: Long,
    val productName: String,
    val originalPrice: Long,
    val dealPrice: Long,
    val openAt: LocalDateTime,
    val closeAt: LocalDateTime,
    val status: DealStatus,
)

data class DealDetail(
    val dealId: Long,
    val productName: String,
    val originalPrice: Long,
    val dealPrice: Long,
    val openAt: LocalDateTime,
    val closeAt: LocalDateTime,
    val status: DealStatus,
    val totalQuantity: Int,
    val perMemberLimit: Int,
    val remainingQuantity: Int,
)
