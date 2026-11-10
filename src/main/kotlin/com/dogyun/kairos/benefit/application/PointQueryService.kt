package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.benefit.domain.PointRepository
import com.dogyun.kairos.common.domain.PageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class PointQueryService(
    private val pointRepository: PointRepository,
    private val clock: Clock,
) {

    fun getMine(memberId: Long): PointSummary {
        val now = LocalDateTime.now(clock)
        val balance = pointRepository.findByMemberId(memberId)?.balance ?: 0L
        return PointSummary(balance, pointRepository.sumExpiringBetween(memberId, now, now.plusDays(30)))
    }

    fun listTransactions(memberId: Long, page: Int, size: Int): PageResult<PointTransactionResult> {
        checkedPage(page)
        checkedSize(size)
        val now = LocalDateTime.now(clock)
        val result = pointRepository.findPageTransactions(memberId, page, size)
        val content = result.content.map {
            PointTransactionResult(
                requireNotNull(it.id), it.type.name, it.amount, it.balanceAfter, it.refType.name, it.refId, it.createdAt ?: now,
            )
        }
        return PageResult(content, result.page, result.size, result.totalElements, result.totalPages, result.hasNext)
    }

    fun getForAdmin(memberId: Long, page: Int, size: Int): AdminMemberPoints {
        val summary = getMine(memberId)
        return AdminMemberPoints(summary.balance, summary.expiringWithin30Days, listTransactions(memberId, page, size))
    }
}
