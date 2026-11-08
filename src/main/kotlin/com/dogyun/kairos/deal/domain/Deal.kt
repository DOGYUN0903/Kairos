package com.dogyun.kairos.deal.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.domain.Money
import java.time.LocalDateTime

class Deal(
    val id: DealId?,
    productName: String,
    originalPrice: Money,
    dealPrice: Money,
    totalQuantity: Int,
    perMemberLimit: Int,
    openAt: LocalDateTime,
    closeAt: LocalDateTime,
) {
    var productName: String = productName
        private set
    var originalPrice: Money = originalPrice
        private set
    var dealPrice: Money = dealPrice
        private set
    var totalQuantity: Int = totalQuantity
        private set
    var perMemberLimit: Int = perMemberLimit
        private set
    var openAt: LocalDateTime = openAt
        private set
    var closeAt: LocalDateTime = closeAt
        private set

    init {
        validate(productName, originalPrice, dealPrice, totalQuantity, perMemberLimit, openAt, closeAt)
    }

    /** openAt <= now < closeAt 이면 ONGOING. */
    fun statusAt(now: LocalDateTime): DealStatus = when {
        now < openAt -> DealStatus.SCHEDULED
        now < closeAt -> DealStatus.ONGOING
        else -> DealStatus.CLOSED
    }

    fun update(
        productName: String,
        originalPrice: Money,
        dealPrice: Money,
        totalQuantity: Int,
        perMemberLimit: Int,
        openAt: LocalDateTime,
        closeAt: LocalDateTime,
        now: LocalDateTime,
    ) {
        ensureNotOpened(now)
        validate(productName, originalPrice, dealPrice, totalQuantity, perMemberLimit, openAt, closeAt)
        this.productName = productName
        this.originalPrice = originalPrice
        this.dealPrice = dealPrice
        this.totalQuantity = totalQuantity
        this.perMemberLimit = perMemberLimit
        this.openAt = openAt
        this.closeAt = closeAt
    }

    fun ensureDeletable(now: LocalDateTime) = ensureNotOpened(now)

    private fun ensureNotOpened(now: LocalDateTime) {
        if (statusAt(now) != DealStatus.SCHEDULED) throw DomainException(ErrorCode.DEAL_ALREADY_OPENED)
    }

    private companion object {
        fun validate(
            productName: String,
            originalPrice: Money,
            dealPrice: Money,
            totalQuantity: Int,
            perMemberLimit: Int,
            openAt: LocalDateTime,
            closeAt: LocalDateTime,
        ) {
            val valid = productName.isNotBlank() &&
                dealPrice.amount <= originalPrice.amount &&
                totalQuantity >= 1 &&
                perMemberLimit >= 1 &&
                closeAt.isAfter(openAt)
            if (!valid) throw DomainException(ErrorCode.DEAL_INVALID)
        }
    }
}
