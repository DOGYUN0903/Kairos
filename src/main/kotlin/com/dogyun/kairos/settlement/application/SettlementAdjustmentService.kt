package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.settlement.domain.SettlementAdjustment
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 수동 조정. 정산에 붙은 조정은 불변이다: 삭제는 "아직 안 붙은 MANUAL 만" 조건부 UPDATE 한 문장으로 하고,
 * 붙이는 UPDATE(생성 트랜잭션)와 같은 행 잠금으로 직렬화되므로 읽고 나서 정하는 경합이 없다.
 */
@Service
@Transactional
class SettlementAdjustmentService(
    private val adjustmentRepository: SettlementAdjustmentRepository,
    private val sellerReader: SettlementSellerReader,
) {

    /** 정지·탈퇴 판매자에게도 등록할 수 있다(판매자 행이 있으면 된다). 다음 정산 생성 때 붙는다. */
    fun create(adminId: Long, sellerId: Long, cmd: CreateAdjustmentCommand): Long {
        requireSeller(sellerId)
        val a = SettlementAdjustment.manual(sellerId, cmd.amount, cmd.reason, cmd.refSellerOrderId, adminId)
        return requireNotNull(adjustmentRepository.save(a).id)
    }

    fun delete(adjustmentId: Long) {
        // 읽기 전에 조건부 UPDATE 부터 한다(0행일 때만 이유를 읽는다).
        if (adjustmentRepository.deleteUnattachedManual(adjustmentId)) return
        adjustmentRepository.findById(adjustmentId) ?: throw DomainException(ErrorCode.SETTLEMENT_NOT_FOUND)
        // 붙은 조정, 그리고 시스템이 만든 CARRY_OVER(UNIQUE 가 삭제 행도 세므로 지우지 않는다) 모두 바꿀 수 없다.
        throw DomainException(ErrorCode.SETTLEMENT_ADJUSTMENT_APPLIED)
    }

    @Transactional(readOnly = true)
    fun listBySeller(sellerId: Long, page: Int, size: Int): PageResult<SettlementAdjustmentResult> {
        requireSeller(sellerId)
        val result = adjustmentRepository.findPageBySellerId(sellerId, page, size)
        return PageResult(result.content.map(::toResult), result.page, result.size, result.totalElements, result.totalPages, result.hasNext)
    }

    private fun requireSeller(sellerId: Long) {
        if (sellerReader.findSellers(listOf(sellerId))[sellerId] == null) throw DomainException(ErrorCode.SELLER_NOT_FOUND)
    }
}

internal fun toResult(a: SettlementAdjustment) = SettlementAdjustmentResult(
    requireNotNull(a.id), a.sellerId, a.settlementId, a.type, a.amount, a.reason, a.refSellerOrderId, a.sourceSettlementId, a.createdAt,
)
