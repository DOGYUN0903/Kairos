package com.dogyun.kairos.catalog.application

/** valueId 가 있으면 기존 값을 유지한다(해당 조합 SKU 가 유지된다). */
data class OptionValueCommand(val valueId: Long?, val name: String)

data class OptionCommand(val optionId: Long?, val name: String, val values: List<OptionValueCommand>)

/** 심사 대상이 되는 상품 내용. 가격은 포함하지 않는다. */
data class ProductContentCommand(
    val name: String,
    val description: String,
    val categoryId: Long,
    val imageFileIds: List<Long>,
    val options: List<OptionCommand>,
)

data class RegisterProductCommand(
    val brandId: Long,
    val content: ProductContentCommand,
    val originalPrice: Long,
    val salePrice: Long,
)

data class ChangeSalePriceCommand(val salePrice: Long)

/** 판매 중인 상품의 내용·정가 변경 요청(Task 10). */
data class SubmitChangeRequestCommand(val content: ProductContentCommand, val originalPrice: Long)

data class UpdateSkuCommand(val extraPrice: Long, val stock: Int, val onSale: Boolean)
