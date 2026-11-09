package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductOption
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.common.security.SellerPrincipal

/**
 * 상품 등록·수정·변경 요청이 공통으로 쓰는 콘텐츠 검증.
 * 카테고리는 소분류, 이미지는 전부 존재하는 본인 업로드 이미지여야 한다. 옵션 값 ID 의 소속은 [com.dogyun.kairos.catalog.domain.Product] 가 검사한다.
 */
internal fun validatedContent(
    seller: SellerPrincipal,
    command: ProductContentCommand,
    categoryQueryService: CategoryQueryService,
    imageFileReader: ImageFileReader,
): ProductContent {
    val content = ProductContent(
        name = command.name,
        description = command.description,
        categoryId = command.categoryId,
        imageFileIds = command.imageFileIds,
        options = command.options.map { option ->
            ProductOption(option.optionId, option.name, option.values.map { OptionValue(it.valueId, it.name) })
        },
    )
    categoryQueryService.requireLeaf(content.categoryId)
    val files = imageFileReader.findImages(content.imageFileIds)
    val owned = content.imageFileIds.all { id ->
        files[id]?.let { it.isImage && it.uploaderMemberId == seller.memberId } ?: false
    }
    if (!owned) throw DomainException(ErrorCode.FILE_NOT_OWNED)
    return content
}
