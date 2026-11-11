package com.dogyun.kairos.seller.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

class Seller(
    val id: SellerId?,
    val memberId: Long,
    companyName: String,
    businessNumber: BusinessNumber,
    representativeName: String,
    phone: String,
    settlementAccount: SettlementAccount,
    commissionRateBp: Int,
    status: SellerStatus,
    rejectReason: String?,
    suspendReason: String?,
    /** 신청 일시. 읽기 전용이며 저장소에서 읽어 온 애그리거트에만 값이 있다. */
    val createdAt: LocalDateTime? = null,
) {
    var companyName: String = companyName
        private set
    var businessNumber: BusinessNumber = businessNumber
        private set
    var representativeName: String = representativeName
        private set
    var phone: String = phone
        private set
    var settlementAccount: SettlementAccount = settlementAccount
        private set
    var commissionRateBp: Int = commissionRateBp
        private set
    var status: SellerStatus = status
        private set
    var rejectReason: String? = rejectReason
        private set
    var suspendReason: String? = suspendReason
        private set

    val canManage: Boolean get() = status == SellerStatus.APPROVED
    val canView: Boolean get() = status == SellerStatus.APPROVED || status == SellerStatus.SUSPENDED

    /** 반려된 신청을 고치면 다시 심사 대기(PENDING)가 되고 반려 사유는 지워진다. */
    fun updateApplication(
        companyName: String,
        businessNumber: BusinessNumber,
        representativeName: String,
        phone: String,
        settlementAccount: SettlementAccount,
    ) {
        transit(setOf(SellerStatus.PENDING, SellerStatus.REJECTED), SellerStatus.PENDING)
        this.companyName = companyName
        this.businessNumber = businessNumber
        this.representativeName = representativeName
        this.phone = phone
        this.settlementAccount = settlementAccount
        this.rejectReason = null
    }

    fun approve(commissionRateBp: Int) {
        if (commissionRateBp !in 0..MAX_COMMISSION_RATE_BP) throw DomainException(ErrorCode.SELLER_INVALID)
        transit(setOf(SellerStatus.PENDING), SellerStatus.APPROVED)
        this.commissionRateBp = commissionRateBp
    }

    fun reject(reason: String) {
        transit(setOf(SellerStatus.PENDING), SellerStatus.REJECTED)
        this.rejectReason = reason
    }

    fun suspend(reason: String) {
        transit(setOf(SellerStatus.APPROVED), SellerStatus.SUSPENDED)
        this.suspendReason = reason
    }

    fun unsuspend() {
        transit(setOf(SellerStatus.SUSPENDED), SellerStatus.APPROVED)
        this.suspendReason = null
    }

    private fun transit(from: Set<SellerStatus>, to: SellerStatus) {
        if (status !in from) throw DomainException(ErrorCode.SELLER_INVALID_STATE_TRANSITION)
        status = to
    }

    companion object {
        const val MAX_COMMISSION_RATE_BP = 10000

        fun apply(
            memberId: Long,
            companyName: String,
            businessNumber: BusinessNumber,
            representativeName: String,
            phone: String,
            settlementAccount: SettlementAccount,
        ) = Seller(
            id = null,
            memberId = memberId,
            companyName = companyName,
            businessNumber = businessNumber,
            representativeName = representativeName,
            phone = phone,
            settlementAccount = settlementAccount,
            commissionRateBp = 0,
            status = SellerStatus.PENDING,
            rejectReason = null,
            suspendReason = null,
        )

        fun restore(
            id: SellerId,
            memberId: Long,
            companyName: String,
            businessNumber: BusinessNumber,
            representativeName: String,
            phone: String,
            settlementAccount: SettlementAccount,
            commissionRateBp: Int,
            status: SellerStatus,
            rejectReason: String?,
            suspendReason: String?,
            createdAt: LocalDateTime? = null,
        ) = Seller(
            id, memberId, companyName, businessNumber, representativeName, phone,
            settlementAccount, commissionRateBp, status, rejectReason, suspendReason, createdAt,
        )
    }
}
