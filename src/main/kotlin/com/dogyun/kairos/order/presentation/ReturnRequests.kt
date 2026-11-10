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
