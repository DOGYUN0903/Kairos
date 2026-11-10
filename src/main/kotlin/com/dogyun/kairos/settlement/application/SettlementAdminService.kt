package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 관리자 정산 확정·지급 처리(조회는 SettlementQueryService). 상태 전이는 정산 행을 `FOR UPDATE` 로 잠근 뒤 현재 상태로 정한다
 * (GENERATED → CONFIRMED → PAID, 건너뛰기 없음, 되풀이는 변화 없이 200).
 */
@Service
@Transactional
class SettlementAdminService(
    private val settlementRepository: SettlementRepository,
    private val queryService: SettlementQueryService,
    private val clock: Clock,
) {

    fun confirm(settlementId: Long): SettlementDetail {
        val s = lock(settlementId)
        s.confirm(LocalDateTime.now(clock))
        return queryService.toDetail(settlementRepository.save(s))
    }

    fun markPaid(settlementId: Long, memo: String?): SettlementDetail {
        val s = lock(settlementId)
        s.markPaid(LocalDateTime.now(clock), memo)
        return queryService.toDetail(settlementRepository.save(s))
    }

    private fun lock(id: Long): Settlement = settlementRepository.findByIdForUpdate(id) ?: throw DomainException(ErrorCode.SETTLEMENT_NOT_FOUND)
}
