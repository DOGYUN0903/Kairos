package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductOption

/**
 * product_change_request.content_json 의 직렬화 형식. 도메인 [ProductContent] 를 직접 직렬화하지 않고
 * 이 DTO 로 옮겨 적어 도메인 클래스가 JSON 형식에 묶이지 않게 한다. 옵션·값 ID 는 유지 여부(SKU 조합)를 뜻하므로 함께 담는다.
 */
data class ProductContentJson(
    val name: String,
    val description: String,
    val categoryId: Long,
    val imageFileIds: List<Long>,
    val options: List<OptionJson>,
) {
    data class OptionJson(val id: Long?, val name: String, val values: List<ValueJson>)

    data class ValueJson(val id: Long?, val name: String)

    fun toDomain() = ProductContent(
        name, description, categoryId, imageFileIds,
        options.map { option -> ProductOption(option.id, option.name, option.values.map { OptionValue(it.id, it.name) }) },
    )

    companion object {
        fun from(content: ProductContent) = ProductContentJson(
            content.name, content.description, content.categoryId, content.imageFileIds,
            content.options.map { option -> OptionJson(option.id, option.name, option.values.map { ValueJson(it.id, it.name) }) },
        )
    }
}
