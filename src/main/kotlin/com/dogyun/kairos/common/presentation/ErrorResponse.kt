package com.dogyun.kairos.common.presentation

import java.time.OffsetDateTime

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: OffsetDateTime = KstTime.now(),
)
