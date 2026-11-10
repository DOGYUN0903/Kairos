package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.Order
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.ReturnRequest
import com.dogyun.kairos.order.domain.ReturnStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** 구매자·판매자·관리자용 반품 조회. 남의 반품은 존재 여부를 숨기려 `RETURN_NOT_FOUND` 로 답한다. */
@Service
@Transactional(readOnly = true)
class ReturnQueryService(
    private val orderRepository: OrderRepository,
    @Suppress("unused") private val clock: Clock,
) {

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
        return order.returnResult(returnId)
    }

    private fun list(memberId: Long?, sellerId: Long?, status: ReturnStatus?, page: Int, size: Int): PageResult<ReturnRequestResult> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val found = orderRepository.findReturnPage(memberId, sellerId, status, page, size)
        val orders = mutableMapOf<Long, Order?>()
        val content = found.content.map { r ->
            val item = orders.getOrPut(r.orderId) { orderRepository.findById(r.orderId) }?.findItem(r.orderItemId)
            r.toResult(item?.productName ?: "", item?.quantity ?: 0)
        }
        return PageResult(content, found.page, found.size, found.totalElements, found.totalPages, found.hasNext)
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
