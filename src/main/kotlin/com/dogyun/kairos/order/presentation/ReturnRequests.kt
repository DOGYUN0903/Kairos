package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.order.application.RequestReturnCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RequestReturnRequest(
    @field:NotBlank val reason: String,
    @field:Size(max = 500) val detail: String? = null,
) {
    fun toCommand() = RequestReturnCommand(reason, detail)
}

/** 누락·공백은 서비스(도메인)가 `RETURN_INVALID` 로 막는다. */
data class RejectReturnRequest(val rejectReason: String? = null)
