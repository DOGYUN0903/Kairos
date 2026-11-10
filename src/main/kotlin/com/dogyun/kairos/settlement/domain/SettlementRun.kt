package com.dogyun.kairos.settlement.domain

import java.time.LocalDate
import java.time.LocalDateTime

/** 기간 하나의 정산 생성이 끝났다는 기록(정산이 0개인 기간도 남긴다). */
data class SettlementRun(val periodStart: LocalDate, val generatedAt: LocalDateTime, val sellerCount: Int)
