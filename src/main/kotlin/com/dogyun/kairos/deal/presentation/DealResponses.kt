package com.dogyun.kairos.deal.presentation

import com.dogyun.kairos.common.presentation.KstTime
import com.dogyun.kairos.deal.application.DealDetail
import com.dogyun.kairos.deal.application.DealSummary
import com.dogyun.kairos.deal.domain.DealStatus
import java.time.OffsetDateTime

data class DealSummaryResponse(
    val dealId: Long,
    val productName: String,
    val originalPrice: Long,
    val dealPrice: Long,
    val openAt: OffsetDateTime,
    val closeAt: OffsetDateTime,
    val status: DealStatus,
) {
    companion object {
        fun from(s: DealSummary) = DealSummaryResponse(
            s.dealId, s.productName, s.originalPrice, s.dealPrice,
            KstTime.toOffset(s.openAt), KstTime.toOffset(s.closeAt), s.status,
        )
    }
}

data class DealDetailResponse(
    val dealId: Long,
    val productName: String,
    val originalPrice: Long,
    val dealPrice: Long,
    val openAt: OffsetDateTime,
    val closeAt: OffsetDateTime,
    val status: DealStatus,
    val totalQuantity: Int,
    val perMemberLimit: Int,
    val remainingQuantity: Int,
) {
    companion object {
        fun from(d: DealDetail) = DealDetailResponse(
            d.dealId, d.productName, d.originalPrice, d.dealPrice,
            KstTime.toOffset(d.openAt), KstTime.toOffset(d.closeAt), d.status,
            d.totalQuantity, d.perMemberLimit, d.remainingQuantity,
        )
    }
}
