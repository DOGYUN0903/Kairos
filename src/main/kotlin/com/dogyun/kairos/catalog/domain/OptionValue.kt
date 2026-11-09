package com.dogyun.kairos.catalog.domain

/** 옵션 값. id 가 null 이면 아직 저장되지 않은 새 값이다. SKU 조합은 이 id 로 맞춘다. */
data class OptionValue(val id: Long?, val name: String)
