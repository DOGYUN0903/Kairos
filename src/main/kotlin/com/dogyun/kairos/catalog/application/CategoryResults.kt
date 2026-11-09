package com.dogyun.kairos.catalog.application

data class CategoryNode(
    val categoryId: Long,
    val name: String,
    val displayOrder: Int,
    val children: List<CategoryNode>,
)
