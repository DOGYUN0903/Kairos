package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.PageResult
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerRepository
import com.dogyun.kairos.seller.domain.SellerStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SellerQueryService(private val sellerRepository: SellerRepository) {

    fun getMine(memberId: Long): SellerDetail =
        sellerRepository.findByMemberId(memberId)?.let(::toDetail) ?: throw DomainException(ErrorCode.SELLER_NOT_FOUND)

    fun findSnapshotByMemberId(memberId: Long): SellerSnapshot? =
        sellerRepository.findByMemberId(memberId)?.let {
            SellerSnapshot(
                sellerId = requireNotNull(it.id).value,
                memberId = it.memberId,
                canManage = it.canManage,
                canView = it.canView,
            )
        }

    fun listForAdmin(status: SellerStatus?, page: Int, size: Int): PageResult<SellerDetail> {
        if (page < 0 || size !in 1..MAX_SIZE) throw DomainException(ErrorCode.INVALID_REQUEST)
        val result = sellerRepository.findPage(status, page, size)
        return PageResult(
            content = result.content.map(::toDetail),
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext,
        )
    }

    private fun toDetail(seller: Seller) = SellerDetail(
        sellerId = requireNotNull(seller.id).value,
        memberId = seller.memberId,
        companyName = seller.companyName,
        businessNumber = seller.businessNumber.value,
        representativeName = seller.representativeName,
        phone = seller.phone,
        bank = seller.settlementAccount.bank,
        accountNumber = seller.settlementAccount.accountNumber,
        holder = seller.settlementAccount.holder,
        commissionRateBp = seller.commissionRateBp,
        status = seller.status.name,
        rejectReason = seller.rejectReason,
        suspendReason = seller.suspendReason,
    )

    private companion object {
        const val MAX_SIZE = 100
    }
}
