package com.dogyun.kairos.payment.application

enum class FailReason { PAYMENT_FAILED, PAYMENT_TIMEOUT }

sealed interface PayResult {
    data class Approved(val paymentId: Long) : PayResult
    data class Failed(val reason: FailReason) : PayResult
}
