package com.dogyun.kairos.catalog.domain

/** 변경 요청이 현재 상품과 다른 항목. 선언 순서가 응답 순서다. */
enum class ChangedField { NAME, DESCRIPTION, CATEGORY, IMAGES, OPTIONS, ORIGINAL_PRICE }
