package com.dogyun.kairos.catalog.domain

/** 판매자 상품 목록 조건. [keyword] 는 상품명 부분 일치(이미 다듬어진 값, 비었으면 null). */
data class SellerProductFilter(
    val status: ProductStatus? = null,
    val keyword: String? = null,
    val brandId: Long? = null,
)
