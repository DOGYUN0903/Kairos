package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.settlement.domain.Settlement
import com.dogyun.kairos.settlement.domain.SettlementAdjustmentRepository
import com.dogyun.kairos.settlement.domain.SettlementRepository
import com.dogyun.kairos.settlement.domain.SettlementStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 정산 조회(판매자·관리자). 읽기 전용이라 잠금이 필요 없다.
 * 목록은 라인 없는 헤더(`lineCount` 는 헤더 값)로 응답하고, 상세만 라인·조정을 읽는다.
 * 남의 정산은 존재 여부가 새지 않도록 없는 정산과 똑같이 `SETTLEMENT_NOT_FOUND` 다.
 */
@Service
@Transactional(readOnly = true)
class SettlementQueryService(
    private val settlementRepository: SettlementRepository,
    private val adjustmentRepository: SettlementAdjustmentRepository,
    private val sellerReader: SettlementSellerReader,
) {

    fun listForSeller(sellerId: Long, page: Int, size: Int): PageResult<SettlementSummary> =
        summaries(settlementRepository.findPageBySellerId(sellerId, page, size))

    fun getForSeller(sellerId: Long, settlementId: Long): SettlementDetail {
        val s = settlementRepository.findById(settlementId)?.takeIf { it.sellerId == sellerId }
            ?: throw DomainException(ErrorCode.SETTLEMENT_NOT_FOUND)
        return toDetail(s)
    }

    fun listForAdmin(sellerId: Long?, status: SettlementStatus?, page: Int, size: Int): PageResult<SettlementSummary> =
        summaries(settlementRepository.findPageForAdmin(sellerId, status, page, size))

    fun getForAdmin(settlementId: Long): SettlementDetail {
        val s = settlementRepository.findById(settlementId) ?: throw DomainException(ErrorCode.SETTLEMENT_NOT_FOUND)
        return toDetail(s)
    }

    /** 이미 읽은 정산(라인 포함)을 상세 응답으로 만든다. 상태 전이 서비스가 같은 트랜잭션에서 재사용한다. */
    fun toDetail(s: Settlement): SettlementDetail {
        val id = requireNotNull(s.id)
        val seller = sellerReader.findSellers(listOf(s.sellerId))[s.sellerId]
        return SettlementDetail(
            settlementId = id, sellerId = s.sellerId, sellerName = seller?.companyName.orEmpty(), sellerStatus = seller?.sellerStatus.orEmpty(),
            periodStart = s.period.start, periodEnd = s.period.end, payoutDate = s.period.payoutDate, status = s.status,
            totalAmount = s.totalAmount, payableAmount = s.payableAmount, lineCount = s.lineCount,
            salesAmount = s.salesAmount, sellerCouponDiscount = s.sellerCouponDiscount, commissionAmount = s.commissionAmount,
            shippingFeeOwed = s.shippingFeeOwed, returnFeeBorne = s.returnFeeBorne, adjustmentAmount = s.adjustmentAmount,
            confirmedAt = s.confirmedAt, paidAt = s.paidAt, paidMemo = s.paidMemo,
            lines = s.lines.map {
                val c = it.calc
                SettlementLineResult(
                    requireNotNull(it.id), it.sellerOrderId, it.orderId, it.orderNo, it.confirmedAt, c.salesAmount, c.sellerCouponDiscount,
                    c.commissionBase, c.commissionRateBp, c.commissionAmount, c.shippingFeeOwed, c.returnFeeBorne, c.lineAmount,
                )
            },
            adjustments = adjustmentRepository.findBySettlementId(id).map(::toResult),
        )
    }

    private fun summaries(result: PageResult<Settlement>): PageResult<SettlementSummary> {
        val sellers = sellerReader.findSellers(result.content.map { it.sellerId }.toSet())
        return PageResult(
            result.content.map { s ->
                val seller = sellers[s.sellerId]
                SettlementSummary(
                    requireNotNull(s.id), s.sellerId, seller?.companyName.orEmpty(), seller?.sellerStatus.orEmpty(),
                    s.period.start, s.period.end, s.period.payoutDate, s.status, s.totalAmount, s.payableAmount, s.lineCount,
                )
            },
            result.page, result.size, result.totalElements, result.totalPages, result.hasNext,
        )
    }
}
