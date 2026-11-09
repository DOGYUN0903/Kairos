package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.seller.domain.BusinessNumber
import com.dogyun.kairos.seller.domain.Seller
import com.dogyun.kairos.seller.domain.SellerRepository
import com.dogyun.kairos.seller.domain.SettlementAccount
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SellerService(private val sellerRepository: SellerRepository) {

    @Transactional
    fun apply(memberId: Long, command: SellerApplicationCommand): Long {
        val businessNumber = BusinessNumber(command.businessNumber)
        if (sellerRepository.findByMemberId(memberId) != null) throw DomainException(ErrorCode.SELLER_ALREADY_EXISTS)
        if (sellerRepository.existsByBusinessNumber(businessNumber)) {
            throw DomainException(ErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED)
        }
        val seller = sellerRepository.save(
            Seller.apply(
                memberId = memberId,
                companyName = command.companyName,
                businessNumber = businessNumber,
                representativeName = command.representativeName,
                phone = command.phone,
                settlementAccount = SettlementAccount(command.bank, command.accountNumber, command.holder),
            ),
        )
        return requireNotNull(seller.id).value
    }

    @Transactional
    fun updateApplication(memberId: Long, command: SellerApplicationCommand) {
        val seller = sellerRepository.findByMemberId(memberId) ?: throw DomainException(ErrorCode.SELLER_NOT_FOUND)
        val businessNumber = BusinessNumber(command.businessNumber)
        if (businessNumber != seller.businessNumber && sellerRepository.existsByBusinessNumber(businessNumber)) {
            throw DomainException(ErrorCode.SELLER_BUSINESS_NUMBER_DUPLICATED)
        }
        seller.updateApplication(
            companyName = command.companyName,
            businessNumber = businessNumber,
            representativeName = command.representativeName,
            phone = command.phone,
            settlementAccount = SettlementAccount(command.bank, command.accountNumber, command.holder),
        )
        sellerRepository.save(seller)
    }
}
