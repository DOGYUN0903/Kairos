package com.dogyun.kairos.order.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.order.domain.CancelReason
import com.dogyun.kairos.order.domain.OrderRepository
import com.dogyun.kairos.order.domain.OrderStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class OrderSummary(
    val orderId: Long,
    val dealId: Long,
    val productName: String,
    val quantity: Int,
    val totalAmount: Long,
    val status: OrderStatus,
    val cancelReason: CancelReason?,
    val createdAt: LocalDateTime,
)

@Service
@Transactional(readOnly = true)
class OrderQueryService(
    private val orderRepository: OrderRepository,
    private val dealReader: DealReader,
) {

    /** 내 주문 최신순. 상품명은 deal 모듈에서 한 번에 조회한다(삭제된 딜은 빈 문자열). */
    fun findMine(memberId: Long, page: Int, size: Int): PageResult<OrderSummary> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val result = orderRepository.findPageByMemberId(memberId, page, size)
        val names = dealReader.getProductNames(result.content.map { it.dealId }.distinct())
        return PageResult(
            content = result.content.map {
                OrderSummary(
                    orderId = checkNotNull(it.id),
                    dealId = it.dealId,
                    productName = names[it.dealId].orEmpty(),
                    quantity = it.quantity,
                    totalAmount = it.amount.amount,
                    status = it.status,
                    cancelReason = it.cancelReason,
                    createdAt = checkNotNull(it.createdAt),
                )
            },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
