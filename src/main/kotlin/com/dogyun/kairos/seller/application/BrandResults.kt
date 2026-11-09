package com.dogyun.kairos.seller.application

import com.dogyun.kairos.seller.domain.Brand

data class BrandDetail(
    val brandId: Long,
    val sellerId: Long,
    val name: String,
    val englishName: String?,
    val logoUrl: String?,
    val description: String?,
    val status: String,
    val rejectReason: String?,
)

data class BrandPublic(
    val brandId: Long,
    val name: String,
    val englishName: String?,
    val logoUrl: String?,
    val description: String?,
)

/** 다른 모듈이 브랜드를 확인할 때 쓰는 요약. 도메인 타입을 노출하지 않는다. */
data class BrandSnapshot(
    val brandId: Long,
    val sellerId: Long,
    val name: String,
    val approved: Boolean,
)

/** 파일 모듈에서 로고 URL 을 한 번에 조회해 상세로 변환한다. */
internal fun toBrandDetails(brands: List<Brand>, imageFileReader: ImageFileReader): List<BrandDetail> {
    val urls = imageFileReader.findImages(brands.mapNotNull { it.logoFileId }).mapValues { it.value.url }
    return brands.map {
        BrandDetail(
            brandId = requireNotNull(it.id).value,
            sellerId = it.sellerId,
            name = it.name,
            englishName = it.englishName,
            logoUrl = it.logoFileId?.let(urls::get),
            description = it.description,
            status = it.status.name,
            rejectReason = it.rejectReason,
        )
    }
}
