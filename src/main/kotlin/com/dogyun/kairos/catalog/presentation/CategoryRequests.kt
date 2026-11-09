package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.CreateCategoryCommand
import com.dogyun.kairos.catalog.application.UpdateCategoryCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CategoryRequest(
    /** 생성에서만 쓰고 수정에서는 무시한다. */
    val parentId: Long? = null,
    @field:NotBlank @field:Size(max = 50) val name: String,
    @field:NotNull val displayOrder: Int,
) {
    fun toCreateCommand() = CreateCategoryCommand(parentId, name, displayOrder)
    fun toUpdateCommand() = UpdateCategoryCommand(name, displayOrder)
}

data class CreateCategoryResponse(val categoryId: Long)
