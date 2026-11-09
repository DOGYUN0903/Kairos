package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.common.infrastructure.BaseEntity
import com.dogyun.kairos.seller.domain.SellerStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "seller")
@SQLRestriction("status = 'ACTIVE'")
class SellerEntity(
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    companyName: String,
    businessNumber: String,
    representativeName: String,
    phone: String,
    bank: String,
    accountNumber: String,
    accountHolder: String,
    commissionRateBp: Int,
    sellerStatus: SellerStatus,
    rejectReason: String?,
    suspendReason: String?,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L

    @Column(name = "company_name", nullable = false, length = 100)
    var companyName: String = companyName
        protected set

    @Column(name = "business_number", nullable = false, length = 12)
    var businessNumber: String = businessNumber
        protected set

    @Column(name = "representative_name", nullable = false, length = 50)
    var representativeName: String = representativeName
        protected set

    @Column(name = "phone", nullable = false, length = 20)
    var phone: String = phone
        protected set

    @Column(name = "bank", nullable = false, length = 50)
    var bank: String = bank
        protected set

    @Column(name = "account_number", nullable = false, length = 50)
    var accountNumber: String = accountNumber
        protected set

    @Column(name = "account_holder", nullable = false, length = 50)
    var accountHolder: String = accountHolder
        protected set

    @Column(name = "commission_rate_bp", nullable = false)
    var commissionRateBp: Int = commissionRateBp
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_status", nullable = false, length = 20)
    var sellerStatus: SellerStatus = sellerStatus
        protected set

    @Column(name = "reject_reason", length = 500)
    var rejectReason: String? = rejectReason
        protected set

    @Column(name = "suspend_reason", length = 500)
    var suspendReason: String? = suspendReason
        protected set

    fun change(
        companyName: String,
        businessNumber: String,
        representativeName: String,
        phone: String,
        bank: String,
        accountNumber: String,
        accountHolder: String,
        commissionRateBp: Int,
        sellerStatus: SellerStatus,
        rejectReason: String?,
        suspendReason: String?,
    ) {
        this.companyName = companyName
        this.businessNumber = businessNumber
        this.representativeName = representativeName
        this.phone = phone
        this.bank = bank
        this.accountNumber = accountNumber
        this.accountHolder = accountHolder
        this.commissionRateBp = commissionRateBp
        this.sellerStatus = sellerStatus
        this.rejectReason = rejectReason
        this.suspendReason = suspendReason
    }
}
