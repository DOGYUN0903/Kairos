package com.dogyun.kairos.catalog.domain

/** 사용자 상품 목록 정렬. LATEST 는 onSaleAt, PRICE_* 는 상품 판매가(SKU 추가 금액 미반영), POPULAR 는 salesCount(동률은 최신순), LIKES 는 likeCount(동률은 id 내림차순). */
enum class ProductSort { LATEST, PRICE_ASC, PRICE_DESC, POPULAR, LIKES }

/**
 * 사용자 상품 검색 조건. 노출 조건(ON_SALE + 브랜드 APPROVED + 판매자 APPROVED)은 저장소가 항상 건다.
 * [leafCategoryIds] 는 대분류를 소분류 목록으로 풀어 넘긴다(null 이면 카테고리 필터 없음).
 * [keyword] 는 상품명·브랜드명 부분 일치이며 `%`, `_` 는 문자로 취급한다.
 */
data class ProductSearchCondition(
    val leafCategoryIds: List<Long>?,
    val brandId: Long?,
    val minPrice: Long?,
    val maxPrice: Long?,
    val keyword: String?,
    val sort: ProductSort,
)

/** 목록 한 줄. 대표 이미지는 파일 ID 로만 들고 URL 은 애플리케이션이 붙인다. */
data class ProductListRow(
    val productId: Long,
    val mainFileId: Long,
    val brandName: String,
    val name: String,
    val originalPrice: Long,
    val salePrice: Long,
    val soldOut: Boolean,
    val likeCount: Long,
    val reviewCount: Long,
    val ratingSum: Long,
)
