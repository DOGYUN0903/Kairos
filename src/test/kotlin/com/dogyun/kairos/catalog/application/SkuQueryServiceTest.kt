package com.dogyun.kairos.catalog.application

import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductOption
import com.dogyun.kairos.catalog.domain.ProductStatus
import com.dogyun.kairos.catalog.domain.Sku
import com.dogyun.kairos.catalog.domain.SkuId
import com.dogyun.kairos.catalog.domain.SkuStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class SkuQueryServiceTest {

    private class FakeSellerReader : SellerReader {
        override fun findBrand(brandId: Long): BrandSnapshot? = null
        override fun getBrandNames(brandIds: Collection<Long>): Map<Long, String> = brandIds.associateWith { "브랜드$it" }
        override fun getSellerNames(sellerIds: Collection<Long>): Map<Long, String> = emptyMap()
    }

    private class FakeImageFileReader : ImageFileReader {
        override fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> =
            fileIds.associateWith { ImageFileRef(it, "http://files/$it.png", 1L, true) }
    }

    private val products = FakeProductRepository()
    private val service = SkuQueryService(products, FakeSellerReader(), FakeImageFileReader())

    private fun colorSize() = listOf(
        ProductOption(null, "색상", listOf(OptionValue(null, "블랙"), OptionValue(null, "화이트"))),
        ProductOption(null, "사이즈", listOf(OptionValue(null, "M"), OptionValue(null, "L"))),
    )

    /** 모든 SKU 재고 [stock], 판매 중인 상품. [status] 로 상태를 맞춘다. */
    private fun product(options: List<ProductOption> = colorSize(), stock: Int = 5, status: ProductStatus = ProductStatus.ON_SALE): Product {
        val registered = products.save(
            Product.register(7L, 10L, ProductContent("티셔츠", "설명", 3L, listOf(100L, 101L), options), 10000, 9000),
        )
        registered.skus.forEach { registered.updateSku(requireNotNull(it.id), 500, stock, true) }
        return transit(registered, status)
    }

    private fun transit(product: Product, status: ProductStatus): Product {
        val now = LocalDateTime.of(2026, 11, 10, 10, 0)
        product.approve(now)
        when (status) {
            ProductStatus.PAUSED -> product.pause()
            ProductStatus.ENDED -> product.end()
            else -> Unit
        }
        return products.save(product)
    }

    @Test
    fun ON_SALE_상품의_재고_있는_SKU는_AVAILABLE이다() {
        val product = product()
        val skuId = requireNotNull(product.skus.first().id).value

        val sku = assertNotNull(service.findOrderableSkus(listOf(skuId))[skuId])

        assertEquals(SkuAvailability.AVAILABLE, sku.availability)
        assertEquals(requireNotNull(product.id).value, sku.productId)
        assertEquals("티셔츠", sku.productName)
        assertEquals(10L, sku.brandId)
        assertEquals("브랜드10", sku.brandName)
        assertEquals("http://files/100.png", sku.thumbnailUrl)
        assertEquals(9000, sku.salePrice)
        assertEquals(500, sku.extraPrice)
        assertEquals(7L, sku.sellerId)
    }

    @Test
    fun 대표_이미지는_상품의_첫_이미지이고_없는_상품은_빠진다() {
        val product = product()
        val productId = requireNotNull(product.id).value

        assertEquals(mapOf(productId to "http://files/100.png"), service.findThumbnailUrls(listOf(productId, 99_999L)))
        assertEquals(emptyMap(), service.findThumbnailUrls(emptyList()))
    }

    @Test
    fun 재고_0은_SOLD_OUT_onSale_false는_PAUSED_PAUSED_상품도_PAUSED() {
        val soldOut = product(stock = 0)
        val soldOutId = requireNotNull(soldOut.skus.first().id).value
        val off = product()
        val offSku = off.skus.first()
        off.updateSku(requireNotNull(offSku.id), 500, 5, false)
        products.save(off)
        val offId = requireNotNull(offSku.id).value
        val paused = product(status = ProductStatus.PAUSED)
        val pausedId = requireNotNull(paused.skus.first().id).value

        val result = service.findOrderableSkus(listOf(soldOutId, offId, pausedId))

        assertEquals(SkuAvailability.SOLD_OUT, result.getValue(soldOutId).availability)
        assertEquals(SkuAvailability.PAUSED, result.getValue(offId).availability)
        assertEquals(SkuAvailability.PAUSED, result.getValue(pausedId).availability)
    }

    @Test
    fun DISCONTINUED_SKU는_DISCONTINUED_ENDED_상품은_ENDED() {
        val base = product()
        val withDiscontinued = products.save(
            Product.restore(
                requireNotNull(base.id), base.sellerId, base.brandId, base.content, base.originalPrice, base.salePrice, base.status,
                base.rejectReason, base.onSaleAt, base.salesCount,
                base.skus + Sku(SkuId(900L), listOf(999L), 0, 3, true, SkuStatus.DISCONTINUED),
            ),
        )
        val discontinuedId = requireNotNull(withDiscontinued.skus.last().id).value
        val ended = product(status = ProductStatus.ENDED)
        val endedId = requireNotNull(ended.skus.first().id).value

        val result = service.findOrderableSkus(listOf(discontinuedId, endedId))

        assertEquals(SkuAvailability.DISCONTINUED, result.getValue(discontinuedId).availability)
        assertEquals(SkuAvailability.ENDED, result.getValue(endedId).availability)
    }

    @Test
    fun 없는_SKU_ID는_결과에서_빠진다() {
        val product = product()
        val skuId = requireNotNull(product.skus.first().id).value

        val result = service.findOrderableSkus(listOf(skuId, 9999L))

        assertEquals(setOf(skuId), result.keys)
        assertFalse(9999L in result)
    }

    @Test
    fun 표시명은_옵션_순서대로_값_이름을_잇는다() {
        val product = product()
        val valueIds = product.content.options.map { option -> option.values.map { it.id } }
        val black = valueIds[0][0]
        val large = valueIds[1][1]
        val target = product.skus.first { it.optionValueIds == listOf(black, large).map { id -> requireNotNull(id) }.sorted() }
        val skuId = requireNotNull(target.id).value

        assertEquals("블랙 / L", service.findOrderableSkus(listOf(skuId)).getValue(skuId).optionDisplayName)
    }
}
