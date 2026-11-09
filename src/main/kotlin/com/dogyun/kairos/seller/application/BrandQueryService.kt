package com.dogyun.kairos.seller.application

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.seller.domain.BrandId
import com.dogyun.kairos.seller.domain.BrandRepository
import com.dogyun.kairos.seller.domain.BrandStatus
import com.dogyun.kairos.seller.domain.SellerId
import com.dogyun.kairos.seller.domain.SellerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BrandQueryService(
    private val brandRepository: BrandRepository,
    private val sellerRepository: SellerRepository,
    private val imageFileReader: ImageFileReader,
) {

    /** 브랜드가 APPROVED 이고 판매자도 APPROVED 일 때만 보인다. */
    fun getPublic(brandId: Long): BrandPublic {
        val brand = brandRepository.findById(BrandId(brandId))
            ?.takeIf { it.status == BrandStatus.APPROVED }
            ?: throw DomainException(ErrorCode.BRAND_NOT_FOUND)
        val seller = sellerRepository.findById(SellerId(brand.sellerId))
        if (seller == null || !seller.canManage) throw DomainException(ErrorCode.BRAND_NOT_FOUND)
        val detail = toBrandDetails(listOf(brand), imageFileReader).single()
        return BrandPublic(detail.brandId, detail.name, detail.englishName, detail.logoUrl, detail.description)
    }

    fun findSnapshot(brandId: Long): BrandSnapshot? =
        brandRepository.findById(BrandId(brandId))?.let {
            BrandSnapshot(brandId, it.sellerId, it.name, it.status == BrandStatus.APPROVED)
        }

    fun getNames(brandIds: Collection<Long>): Map<Long, String> =
        brandRepository.findAllByIds(brandIds).associate { requireNotNull(it.id).value to it.name }
}
