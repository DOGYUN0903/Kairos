package com.dogyun.kairos.benefit.application

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

data class PointSummary(val balance: Long, val expiringWithin30Days: Long)

data class PointTransactionResult(
    val transactionId: Long,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val refType: String,
    val refId: Long,
    val createdAt: LocalDateTime,
)

data class AdminMemberPoints(
    val balance: Long,
    val expiringWithin30Days: Long,
    val transactions: PageResult<PointTransactionResult>,
)
