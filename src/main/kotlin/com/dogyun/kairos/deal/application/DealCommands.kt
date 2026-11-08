package com.dogyun.kairos.deal.application

import java.time.LocalDateTime

data class CreateDealCommand(
    val productName: String,
    val originalPrice: Long,
    val dealPrice: Long,
    val totalQuantity: Int,
    val perMemberLimit: Int,
    val openAt: LocalDateTime,
    val closeAt: LocalDateTime,
)

data class UpdateDealCommand(
    val productName: String,
    val originalPrice: Long,
    val dealPrice: Long,
    val totalQuantity: Int,
    val perMemberLimit: Int,
    val openAt: LocalDateTime,
    val closeAt: LocalDateTime,
)
