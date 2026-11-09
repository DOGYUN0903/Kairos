package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.SellerPrincipal
import com.dogyun.kairos.seller.domain.Brand
import com.dogyun.kairos.seller.domain.BrandId
import com.dogyun.kairos.seller.domain.BrandRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandService(
    private val brandRepository: BrandRepository,
    private val imageFileReader: ImageFileReader,
) {

    @Transactional
    fun apply(seller: SellerPrincipal, command: BrandApplicationCommand): Long {
        val brand = Brand.apply(seller.sellerId, command.name, command.englishName, command.logoFileId, command.description)
        if (brandRepository.existsByName(command.name)) throw DomainException(ErrorCode.BRAND_NAME_DUPLICATED)
        requireOwnedLogo(seller, command.logoFileId)
        return requireNotNull(brandRepository.save(brand).id).value
    }

    @Transactional
    fun reapply(seller: SellerPrincipal, brandId: Long, command: BrandApplicationCommand) {
        val brand = brandRepository.findById(BrandId(brandId))?.takeIf { it.sellerId == seller.sellerId }
            ?: throw DomainException(ErrorCode.BRAND_NOT_FOUND)
        if (command.name != brand.name && brandRepository.existsByName(command.name)) {
            throw DomainException(ErrorCode.BRAND_NAME_DUPLICATED)
        }
        requireOwnedLogo(seller, command.logoFileId)
        brand.reapply(command.name, command.englishName, command.logoFileId, command.description)
        brandRepository.save(brand)
    }

    @Transactional(readOnly = true)
    fun listMine(seller: SellerPrincipal): List<BrandDetail> =
        toBrandDetails(brandRepository.findAllBySellerId(seller.sellerId), imageFileReader)

    private fun requireOwnedLogo(seller: SellerPrincipal, logoFileId: Long?) {
        if (logoFileId == null) return
        val file = imageFileReader.findImages(listOf(logoFileId))[logoFileId]
        if (file == null || !file.isImage || file.uploaderMemberId != seller.memberId) {
            throw DomainException(ErrorCode.FILE_NOT_OWNED)
        }
    }
}
