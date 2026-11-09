package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime

/**
 * 상품 애그리거트. 판매자·브랜드·카테고리는 ID 로만 참조한다.
 * 정가 변경은 변경 요청(재검수)을 거쳐 [applyChange] 로만 들어오고,
 * 즉시 반영은 판매가([changeSalePrice])와 SKU 재고·추가 금액·판매 여부([updateSku])뿐이다.
 */
class Product(
    val id: ProductId?,
    val sellerId: Long,
    val brandId: Long,
    content: ProductContent,
    originalPrice: Long,
    salePrice: Long,
    status: ProductStatus,
    rejectReason: String?,
    onSaleAt: LocalDateTime?,
    val salesCount: Long,
    skus: List<Sku>,
) {
    var content: ProductContent = content
        private set
    var originalPrice: Long = originalPrice
        private set
    var salePrice: Long = salePrice
        private set
    var status: ProductStatus = status
        private set
    var rejectReason: String? = rejectReason
        private set

    /** 처음 승인될 때만 기록하고 이후 덮어쓰지 않는다(최신순 정렬 기준). */
    var onSaleAt: LocalDateTime? = onSaleAt
        private set

    private val _skus: MutableList<Sku> = skus.toMutableList()
    val skus: List<Sku> get() = _skus

    val activeSkus: List<Sku> get() = _skus.filter { it.skuStatus == SkuStatus.ACTIVE }

    /** ACTIVE SKU 중 판매 중이고 재고가 있는 것이 하나도 없으면 품절. */
    val soldOut: Boolean get() = activeSkus.none { it.available }

    /** 정수 나눗셈 = 내림. */
    val discountRate: Int get() = ((originalPrice - salePrice) * 100 / originalPrice).toInt()

    /** PENDING_REVIEW·REJECTED 상품의 내용을 고치면 다시 심사 대기가 된다. */
    fun editContent(content: ProductContent) {
        requireStatus(ProductStatus.PENDING_REVIEW, ProductStatus.REJECTED)
        validateValueIds(content)
        this.content = content
        this.status = ProductStatus.PENDING_REVIEW
        this.rejectReason = null
    }

    fun approve(now: LocalDateTime) {
        requireStatus(ProductStatus.PENDING_REVIEW)
        status = ProductStatus.ON_SALE
        rejectReason = null
        if (onSaleAt == null) onSaleAt = now
    }

    fun reject(reason: String) {
        requireStatus(ProductStatus.PENDING_REVIEW)
        status = ProductStatus.REJECTED
        rejectReason = reason
    }

    fun pause() {
        requireStatus(ProductStatus.ON_SALE)
        status = ProductStatus.PAUSED
    }

    fun resume() {
        requireStatus(ProductStatus.PAUSED)
        status = ProductStatus.ON_SALE
    }

    fun end() {
        requireStatus(ProductStatus.ON_SALE, ProductStatus.PAUSED)
        status = ProductStatus.ENDED
    }

    /** 승인된 변경 요청을 반영한다. 상태와 onSaleAt 은 그대로 둔다. */
    fun applyChange(content: ProductContent, originalPrice: Long) {
        requireStatus(ProductStatus.ON_SALE, ProductStatus.PAUSED)
        validateValueIds(content)
        validatePrice(originalPrice, salePrice)
        this.content = content
        this.originalPrice = originalPrice
    }

    /** 판매가만 바꾼다. 정가는 변경 요청으로만 바뀐다. */
    fun changeSalePrice(salePrice: Long) {
        if (status == ProductStatus.ENDED) throw DomainException(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION)
        validatePrice(originalPrice, salePrice)
        this.salePrice = salePrice
    }

    fun updateSku(skuId: SkuId, extraPrice: Long, stock: Int, onSale: Boolean) {
        if (status == ProductStatus.ENDED) throw DomainException(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION)
        val sku = _skus.firstOrNull { it.id == skuId } ?: throw DomainException(ErrorCode.SKU_NOT_FOUND)
        sku.update(extraPrice, stock, onSale)
    }

    /**
     * 저장소가 옵션 값 ID 를 확정한 뒤 호출한다. 조합(값 ID 목록)마다
     * 같은 조합의 ACTIVE SKU 는 유지, DISCONTINUED 는 되살리고(재고 0), 없으면 새로 만든다.
     * 목록에 없는 ACTIVE SKU 는 DISCONTINUED 가 된다. 옵션 축이 없으면 조합은 `listOf(emptyList())` 하나다.
     */
    fun rebuildSkus(valueIdsByCombination: List<List<Long>>) {
        val wantedKeys = valueIdsByCombination.map { Sku.combinationKey(it) }.toSet()
        val existing = _skus.associateBy { it.combinationKey }
        _skus.filter { it.skuStatus == SkuStatus.ACTIVE && it.combinationKey !in wantedKeys }.forEach { it.discontinue() }
        for (valueIds in valueIdsByCombination) {
            val sku = existing[Sku.combinationKey(valueIds)]
            when {
                sku == null -> _skus.add(Sku.create(valueIds))
                sku.skuStatus == SkuStatus.DISCONTINUED -> sku.revive()
            }
        }
    }

    /** 바뀐 콘텐츠가 유지하는 값(id 있음)은 이 상품의 기존 값이어야 한다. */
    private fun validateValueIds(content: ProductContent) {
        if (!this.content.existingValueIds().containsAll(content.existingValueIds())) {
            throw DomainException(ErrorCode.PRODUCT_INVALID)
        }
    }

    private fun requireStatus(vararg allowed: ProductStatus) {
        if (status !in allowed) throw DomainException(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION)
    }

    companion object {
        /** 등록은 심사 대기로 시작한다. 옵션 값은 모두 새 값(id null)이어야 하고 SKU 는 저장소가 값 ID 확정 후 만든다. */
        fun register(sellerId: Long, brandId: Long, content: ProductContent, originalPrice: Long, salePrice: Long): Product {
            if (content.existingValueIds().isNotEmpty() || content.options.any { it.id != null }) {
                throw DomainException(ErrorCode.PRODUCT_INVALID)
            }
            validatePrice(originalPrice, salePrice)
            return Product(null, sellerId, brandId, content, originalPrice, salePrice, ProductStatus.PENDING_REVIEW, null, null, 0, emptyList())
        }

        fun restore(
            id: ProductId,
            sellerId: Long,
            brandId: Long,
            content: ProductContent,
            originalPrice: Long,
            salePrice: Long,
            status: ProductStatus,
            rejectReason: String?,
            onSaleAt: LocalDateTime?,
            salesCount: Long,
            skus: List<Sku>,
        ) = Product(id, sellerId, brandId, content, originalPrice, salePrice, status, rejectReason, onSaleAt, salesCount, skus)

        /** 0 < 판매가 ≤ 정가. */
        private fun validatePrice(originalPrice: Long, salePrice: Long) {
            if (salePrice <= 0 || salePrice > originalPrice) throw DomainException(ErrorCode.PRODUCT_INVALID_PRICE)
        }
    }
}
