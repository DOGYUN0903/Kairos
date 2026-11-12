package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.ReturnRefundCalculator
import com.dogyun.kairos.order.domain.ReturnRequest
import com.dogyun.kairos.order.domain.ReturnStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/** 구매자·판매자·관리자용 반품 조회. 남의 반품은 존재 여부를 숨기려 `RETURN_NOT_FOUND` 로 답한다. */
@Service
@Transactional(readOnly = true)
class ReturnQueryService(
    private val orderRepository: OrderRepository,
    private val productReader: ProductReader,
    private val properties: OrderProperties,
    private val clock: Clock,
) {

    /**
     * 반품 환불 미리보기. 주문 행 락 없이 읽는다(아무것도 바꾸지 않는다). 사유 변환은 반품 요청과 같고 주문 조회보다 먼저다.
     * 자격은 [Order.checkReturnRequestable] 로 요청과 같게 판정하고, 금액은 [ReturnRefundCalculator.preview] 로 계산한다.
     */
    fun previewReturn(memberId: Long, orderId: Long, orderItemId: Long, reason: String): ReturnPreviewResult {
        val returnReason = parseReturnReason(reason)
        val order = orderRepository.findById(orderId)?.takeIf { it.memberId == memberId }
            ?: throw DomainException(ErrorCode.ORDER_NOT_FOUND)
        order.checkReturnRequestable(orderItemId, properties.returnWindow, LocalDateTime.now(clock))
        val plan = ReturnRefundCalculator.preview(order, orderItemId, returnReason, properties.returnFeePolicy())
        return ReturnPreviewResult(
            orderItemId = orderItemId,
            reason = returnReason.name,
            feeBearer = returnReason.feeBearer.name,
            paidAmount = plan.paidAmount,
            returnFee = plan.returnFee,
            outboundFeeRecovered = plan.outboundFeeRecovered,
            shippingFeeRefund = plan.shippingFeeRefund,
            refundAmount = plan.refundAmount,
            pointsRefund = plan.pointsRefund,
            productCouponReturnable = plan.productCouponReturn != null,
        )
    }

    fun getMine(memberId: Long, returnId: Long): ReturnRequestResult = find(returnId) { it.memberId == memberId }

    fun listMine(memberId: Long, page: Int, size: Int): PageResult<ReturnRequestResult> = list(memberId, null, null, page, size)

    fun getForSeller(sellerId: Long, returnId: Long): ReturnRequestResult = find(returnId) { it.sellerId == sellerId }

    fun listForSeller(sellerId: Long, status: ReturnStatus?, page: Int, size: Int): PageResult<ReturnRequestResult> =
        list(null, sellerId, status, page, size)

    fun getForAdmin(returnId: Long): ReturnRequestResult = find(returnId) { true }

    fun listForAdmin(status: ReturnStatus?, sellerId: Long?, memberId: Long?, page: Int, size: Int): PageResult<ReturnRequestResult> =
        list(memberId, sellerId, status, page, size)

    private fun find(returnId: Long, visible: (ReturnRequest) -> Boolean): ReturnRequestResult {
        val orderId = orderRepository.findOrderIdByReturnId(returnId) ?: throw DomainException(ErrorCode.RETURN_NOT_FOUND)
        val order = orderRepository.findById(orderId) ?: throw DomainException(ErrorCode.RETURN_NOT_FOUND)
        val request = requireNotNull(order.findReturn(returnId).second.returnRequest)
        if (!visible(request)) throw DomainException(ErrorCode.RETURN_NOT_FOUND)
        val productId = order.findReturn(returnId).second.productId
        return order.returnResult(returnId, productReader.findThumbnailUrls(listOf(productId)), properties.returnAutoApproveAfter)
    }

    private fun list(memberId: Long?, sellerId: Long?, status: ReturnStatus?, page: Int, size: Int): PageResult<ReturnRequestResult> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val found = orderRepository.findReturnPage(memberId, sellerId, status, page, size)
        val orders = mutableMapOf<Long, Order>()
        found.content.forEach { r -> orders.getOrPut(r.orderId) { requireNotNull(orderRepository.findById(r.orderId)) } }
        val productIds = found.content.map { orders.getValue(it.orderId).findItem(it.orderItemId).productId }.toSet()
        // 한 페이지의 이미지는 한 번에 조회한다(N+1 금지)
        val thumbnails = if (productIds.isEmpty()) emptyMap() else productReader.findThumbnailUrls(productIds)
        val content = found.content.map { r ->
            orders.getValue(r.orderId).returnResult(requireNotNull(r.id), thumbnails, properties.returnAutoApproveAfter)
        }
        return PageResult(content, found.page, found.size, found.totalElements, found.totalPages, found.hasNext)
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
