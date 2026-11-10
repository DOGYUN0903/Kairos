package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.benefit.application.PointTransactionResult
import com.dogyun.kairos.common.presentation.PageResponse

data class AdminMemberPointsResponse(
    val balance: Long,
    val expiringWithin30Days: Long,
    val transactions: PageResponse<PointTransactionResult>,
)
