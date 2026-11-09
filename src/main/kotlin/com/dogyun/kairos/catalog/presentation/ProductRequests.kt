package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.catalog.application.ChangeSalePriceCommand
import com.dogyun.kairos.catalog.application.OptionCommand
import com.dogyun.kairos.catalog.application.OptionValueCommand
import com.dogyun.kairos.catalog.application.ProductContentCommand
import com.dogyun.kairos.catalog.application.RegisterProductCommand
import com.dogyun.kairos.catalog.application.UpdateSkuCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

data class OptionValueRequest(
    val valueId: Long? = null,
    @field:NotBlank @field:Size(max = 50) val name: String,
) {
    fun toCommand() = OptionValueCommand(valueId, name)
}

data class OptionRequest(
    val optionId: Long? = null,
    @field:NotBlank @field:Size(max = 50) val name: String,
    @field:Size(min = 1) @field:Valid val values: List<OptionValueRequest>,
) {
    fun toCommand() = OptionCommand(optionId, name, values.map { it.toCommand() })
}

data class ProductContentRequest(
    @field:NotBlank @field:Size(max = 100) val name: String,
    @field:NotNull val description: String,
    @field:NotNull val categoryId: Long,
    @field:Size(min = 1, max = 10) val imageFileIds: List<Long>,
    @field:Size(max = 2) @field:Valid val options: List<OptionRequest> = emptyList(),
) {
    fun toCommand() = ProductContentCommand(name, description, categoryId, imageFileIds, options.map { it.toCommand() })
}

data class RegisterProductRequest(
    @field:NotNull val brandId: Long,
    @field:NotNull @field:Valid val content: ProductContentRequest,
    @field:NotNull val originalPrice: Long,
    @field:NotNull val salePrice: Long,
) {
    fun toCommand() = RegisterProductCommand(brandId, content.toCommand(), originalPrice, salePrice)
}

data class ChangeSalePriceRequest(@field:NotNull val salePrice: Long) {
    fun toCommand() = ChangeSalePriceCommand(salePrice)
}

data class UpdateSkuRequest(
    @field:NotNull @field:PositiveOrZero val extraPrice: Long,
    @field:NotNull @field:PositiveOrZero val stock: Int,
    @field:NotNull val onSale: Boolean,
) {
    fun toCommand() = UpdateSkuCommand(extraPrice, stock, onSale)
}

data class RegisterProductResponse(val productId: Long)

data class RejectProductRequest(@field:NotBlank @field:Size(max = 500) val reason: String)
