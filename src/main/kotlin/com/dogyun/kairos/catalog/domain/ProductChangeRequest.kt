package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/**
 * 판매 중인 상품의 내용·정가 변경 요청. 상품은 ID 로만 참조하며 승인될 때까지 상품은 그대로다.
 * 정가는 변경 요청으로만 바뀐다. 상태 전이는 모두 PENDING 에서만 가능하다.
 */
class ProductChangeRequest(
    val id: Long?,
    val productId: Long,
    val sellerId: Long,
    val content: ProductContent,
    val originalPrice: Long,
    status: ChangeRequestStatus,
    rejectReason: String?,
) {
    var status: ChangeRequestStatus = status
        private set
    var rejectReason: String? = rejectReason
        private set

    fun approve() {
        requirePending()
        status = ChangeRequestStatus.APPROVED
    }

    fun reject(reason: String) {
        requirePending()
        status = ChangeRequestStatus.REJECTED
        rejectReason = reason
    }

    fun withdraw() {
        requirePending()
        status = ChangeRequestStatus.WITHDRAWN
    }

    private fun requirePending() {
        if (status != ChangeRequestStatus.PENDING) throw DomainException(ErrorCode.CHANGE_REQUEST_INVALID_STATE_TRANSITION)
    }

    companion object {
        fun submit(productId: Long, sellerId: Long, content: ProductContent, originalPrice: Long) =
            ProductChangeRequest(null, productId, sellerId, content, originalPrice, ChangeRequestStatus.PENDING, null)

        fun restore(
            id: Long,
            productId: Long,
            sellerId: Long,
            content: ProductContent,
            originalPrice: Long,
            status: ChangeRequestStatus,
            rejectReason: String?,
        ) = ProductChangeRequest(id, productId, sellerId, content, originalPrice, status, rejectReason)
    }
}
