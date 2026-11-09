package com.dogyun.kairos.catalog.application

data class CreateCategoryCommand(val parentId: Long?, val name: String, val displayOrder: Int)

data class UpdateCategoryCommand(val name: String, val displayOrder: Int)
