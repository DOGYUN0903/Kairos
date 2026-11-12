package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.order.application.RequestReturnCommand
import com.dogyun.kairos.order.application.ReturnPreviewResult
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

data class ReturnPreviewResponse(
    val orderItemId: Long,
    val reason: String,
    val feeBearer: String,
    val paidAmount: Long,
    val returnFee: Long,
    val outboundFeeRecovered: Long,
    val shippingFeeRefund: Long,
    val refundAmount: Long,
    val pointsRefund: Long,
    val productCouponReturnable: Boolean,
) {
    companion object {
        fun from(r: ReturnPreviewResult) = ReturnPreviewResponse(
            r.orderItemId, r.reason, r.feeBearer, r.paidAmount, r.returnFee, r.outboundFeeRecovered,
            r.shippingFeeRefund, r.refundAmount, r.pointsRefund, r.productCouponReturnable,
        )
    }
}
