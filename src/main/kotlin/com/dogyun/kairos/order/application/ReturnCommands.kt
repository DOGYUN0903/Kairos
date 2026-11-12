package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.order.domain.ReturnReason

/** [reason] 은 `CHANGE_OF_MIND`, `DEFECTIVE`, `WRONG_DELIVERY` 중 하나(아니면 `RETURN_INVALID`). */
data class RequestReturnCommand(val reason: String, val detail: String?)

/** 반품 요청·미리보기가 함께 쓰는 사유 변환. 모르는 값은 `RETURN_INVALID`. */
internal fun parseReturnReason(value: String): ReturnReason =
    ReturnReason.entries.firstOrNull { it.name == value } ?: throw DomainException(ErrorCode.RETURN_INVALID)
