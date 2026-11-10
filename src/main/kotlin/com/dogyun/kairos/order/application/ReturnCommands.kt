package com.dogyun.kairos.order.application

/** [reason] 은 `CHANGE_OF_MIND`, `DEFECTIVE`, `WRONG_DELIVERY` 중 하나(아니면 `RETURN_INVALID`). */
data class RequestReturnCommand(val reason: String, val detail: String?)
