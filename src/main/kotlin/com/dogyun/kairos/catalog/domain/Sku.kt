package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 옵션 값 조합 하나. 조합은 옵션 값 ID 로 식별하므로 값 이름이 바뀌어도 같은 SKU 다. */
class Sku(
    val id: SkuId?,
    optionValueIds: List<Long>,
    extraPrice: Long,
    stock: Int,
    onSale: Boolean,
    skuStatus: SkuStatus,
) {
    val optionValueIds: List<Long> = optionValueIds.sorted()
    var extraPrice: Long = extraPrice
        private set
    var stock: Int = stock
        private set
    var onSale: Boolean = onSale
        private set
    var skuStatus: SkuStatus = skuStatus
        private set

    /** 정렬된 값 ID 를 "|" 로 이은 키. 옵션 축이 없으면 "". */
    val combinationKey: String get() = optionValueIds.joinToString("|")

    val available: Boolean get() = skuStatus == SkuStatus.ACTIVE && onSale && stock > 0

    fun update(extraPrice: Long, stock: Int, onSale: Boolean) {
        if (skuStatus == SkuStatus.DISCONTINUED) throw DomainException(ErrorCode.SKU_NOT_FOUND)
        if (extraPrice < 0 || stock < 0) throw DomainException(ErrorCode.SKU_INVALID)
        this.extraPrice = extraPrice
        this.stock = stock
        this.onSale = onSale
    }

    fun discontinue() {
        skuStatus = SkuStatus.DISCONTINUED
    }

    /** 사라졌던 조합이 다시 생기면 같은 행을 되살리되 재고는 0 으로 둔다(스펙 13장). */
    fun revive() {
        skuStatus = SkuStatus.ACTIVE
        stock = 0
    }

    companion object {
        fun create(optionValueIds: List<Long>) = Sku(null, optionValueIds, 0, 0, true, SkuStatus.ACTIVE)

        fun combinationKey(optionValueIds: List<Long>): String = optionValueIds.sorted().joinToString("|")
    }
}
