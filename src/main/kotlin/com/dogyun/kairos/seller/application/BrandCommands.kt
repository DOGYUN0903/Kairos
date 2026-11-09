package com.dogyun.kairos.seller.application

data class BrandApplicationCommand(
    val name: String,
    val englishName: String?,
    val logoFileId: Long?,
    val description: String?,
)
