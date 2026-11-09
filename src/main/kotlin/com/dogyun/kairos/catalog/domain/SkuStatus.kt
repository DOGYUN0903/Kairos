package com.dogyun.kairos.catalog.domain

/** 사라진 옵션 조합은 소프트 딜리트하지 않고 DISCONTINUED 로 둔다(스펙 13장). */
enum class SkuStatus { ACTIVE, DISCONTINUED }
