package com.dogyun.kairos.catalog.application

/** seller 모듈의 브랜드·판매자 정보를 읽는 포트. 구현은 catalog.infrastructure 가 맡는다. */
interface SellerReader {
    fun findBrand(brandId: Long): BrandSnapshot?
    fun getBrandNames(brandIds: Collection<Long>): Map<Long, String>

    /** 판매자 회사명. 한 번의 조회로 구하며 없는 판매자는 결과에 없다. */
    fun getSellerNames(sellerIds: Collection<Long>): Map<Long, String>
}

/** catalog 가 보는 브랜드 요약. seller 모듈의 타입과는 별개다. */
data class BrandSnapshot(
    val brandId: Long,
    val sellerId: Long,
    val name: String,
    val approved: Boolean,
)
