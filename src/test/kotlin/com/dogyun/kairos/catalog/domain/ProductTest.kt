package com.dogyun.kairos.catalog.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 9, 10, 0)

    /** 값 ID는 테스트가 1, 2, …로 직접 준다(저장소가 확정하는 역할을 흉내낸다). */
    private fun colorSizeOptions(withIds: Boolean = true) = listOf(
        ProductOption(
            id = 1.takeIf { withIds }?.toLong(),
            name = "색상",
            values = listOf(OptionValue(1L.takeIf { withIds }, "블랙"), OptionValue(2L.takeIf { withIds }, "화이트")),
        ),
        ProductOption(
            id = 2.takeIf { withIds }?.toLong(),
            name = "사이즈",
            values = listOf(OptionValue(3L.takeIf { withIds }, "S"), OptionValue(4L.takeIf { withIds }, "M")),
        ),
    )

    private fun content(options: List<ProductOption> = emptyList(), imageFileIds: List<Long> = listOf(10L)) =
        ProductContent(name = "티셔츠", description = "설명", categoryId = 5L, imageFileIds = imageFileIds, options = options)

    private fun combinations() = listOf(listOf(1L, 3L), listOf(1L, 4L), listOf(2L, 3L), listOf(2L, 4L))

    private fun register(options: List<ProductOption> = emptyList(), originalPrice: Long = 10000, salePrice: Long = 9000) =
        Product.register(sellerId = 1L, brandId = 2L, content = content(options), originalPrice = originalPrice, salePrice = salePrice)

    private fun restore(
        status: ProductStatus,
        content: ProductContent = content(colorSizeOptions()),
        skus: List<Sku> = emptyList(),
        originalPrice: Long = 10000,
        salePrice: Long = 9000,
        onSaleAt: LocalDateTime? = now,
    ) = Product.restore(
        id = ProductId(100L),
        sellerId = 1L,
        brandId = 2L,
        content = content,
        originalPrice = originalPrice,
        salePrice = salePrice,
        status = status,
        rejectReason = null,
        onSaleAt = onSaleAt,
        salesCount = 0,
        skus = skus,
    )

    private fun sku(id: Long, valueIds: List<Long>, extraPrice: Long = 0, stock: Int = 0, onSale: Boolean = true, status: SkuStatus = SkuStatus.ACTIVE) =
        Sku(SkuId(id), valueIds, extraPrice, stock, onSale, status)

    private fun onSaleWithSkus(skus: List<Sku>) = restore(ProductStatus.ON_SALE, skus = skus)

    private fun assertCode(expected: ErrorCode, block: () -> Unit) {
        val e = assertFailsWith<DomainException> { block() }
        assertEquals(expected, e.errorCode)
    }

    @Test
    fun rebuildSkus는_조합마다_ACTIVE_SKU를_재고_0으로_만든다() {
        val product = register(colorSizeOptions(withIds = false))
        assertTrue(product.skus.isEmpty())

        product.rebuildSkus(combinations())

        assertEquals(4, product.skus.size)
        assertEquals(setOf("1|3", "1|4", "2|3", "2|4"), product.skus.map { it.combinationKey }.toSet())
        assertTrue(product.skus.all { it.skuStatus == SkuStatus.ACTIVE && it.stock == 0 && it.extraPrice == 0L && it.onSale })
    }

    @Test
    fun 옵션이_없으면_SKU_하나() {
        val product = register()

        product.rebuildSkus(listOf(emptyList()))

        assertEquals(1, product.skus.size)
        assertEquals("", product.skus.single().combinationKey)
    }

    @Test
    fun 옵션_3개는_PRODUCT_INVALID() {
        val options = (1..3).map { ProductOption(null, "축$it", listOf(OptionValue(null, "값"))) }
        assertCode(ErrorCode.PRODUCT_INVALID) { content(options) }
    }

    @Test
    fun 이미지_11장은_PRODUCT_INVALID() {
        assertCode(ErrorCode.PRODUCT_INVALID) { content(imageFileIds = (1L..11L).toList()) }
        assertCode(ErrorCode.PRODUCT_INVALID) { content(imageFileIds = emptyList()) }
        assertCode(ErrorCode.PRODUCT_INVALID) { content(imageFileIds = listOf(1L, 1L)) }
    }

    @Test
    fun 옵션_축이나_값_이름이_중복되면_PRODUCT_INVALID() {
        assertCode(ErrorCode.PRODUCT_INVALID) { ProductOption(null, "색상", listOf(OptionValue(null, "블랙"), OptionValue(null, "블랙"))) }
        assertCode(ErrorCode.PRODUCT_INVALID) { ProductOption(null, "색상", emptyList()) }
        val dup = listOf(
            ProductOption(null, "색상", listOf(OptionValue(null, "블랙"))),
            ProductOption(null, "색상", listOf(OptionValue(null, "S"))),
        )
        assertCode(ErrorCode.PRODUCT_INVALID) { content(dup) }
    }

    @Test
    fun 등록_시_값_ID가_있으면_PRODUCT_INVALID() {
        assertCode(ErrorCode.PRODUCT_INVALID) { register(colorSizeOptions(withIds = true)) }
    }

    @Test
    fun 판매가가_정가보다_크면_PRODUCT_INVALID_PRICE() {
        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { register(originalPrice = 10000, salePrice = 10001) }
        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { register(originalPrice = 10000, salePrice = 0) }
    }

    @Test
    fun 할인율은_내림이다() {
        assertEquals(33, register(originalPrice = 10000, salePrice = 6666).discountRate)
        assertEquals(0, register(originalPrice = 10000, salePrice = 10000).discountRate)
    }

    @Test
    fun 승인하면_ON_SALE이고_onSaleAt이_기록된다() {
        val product = register()
        assertEquals(ProductStatus.PENDING_REVIEW, product.status)
        assertNull(product.onSaleAt)

        product.approve(now)

        assertEquals(ProductStatus.ON_SALE, product.status)
        assertEquals(now, product.onSaleAt)
    }

    @Test
    fun 일시중지_재개해도_onSaleAt은_바뀌지_않는다() {
        val product = restore(ProductStatus.ON_SALE, onSaleAt = now)

        product.pause()
        product.resume()

        assertEquals(now, product.onSaleAt)
    }

    @Test
    fun ON_SALE_일시중지_재개_종료() {
        val product = restore(ProductStatus.ON_SALE)

        product.pause()
        assertEquals(ProductStatus.PAUSED, product.status)
        product.resume()
        assertEquals(ProductStatus.ON_SALE, product.status)
        product.pause()
        product.end()
        assertEquals(ProductStatus.ENDED, product.status)
    }

    @Test
    fun 종료_후_재개는_INVALID_STATE_TRANSITION() {
        val product = restore(ProductStatus.ENDED)
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { product.resume() }
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { product.pause() }
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { product.approve(now) }
    }

    @Test
    fun 반려된_상품_내용을_고치면_PENDING_REVIEW() {
        val product = register()
        product.reject("설명 부족")
        assertEquals(ProductStatus.REJECTED, product.status)
        assertEquals("설명 부족", product.rejectReason)

        product.editContent(content(imageFileIds = listOf(11L)))

        assertEquals(ProductStatus.PENDING_REVIEW, product.status)
        assertNull(product.rejectReason)
        assertEquals(listOf(11L), product.content.imageFileIds)
    }

    @Test
    fun ON_SALE_상품은_직접_수정할_수_없다() {
        val product = restore(ProductStatus.ON_SALE)
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { product.editContent(content(colorSizeOptions())) }
        // 반대로 변경 적용은 심사 중 상품에는 쓸 수 없다.
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { register().applyChange(content(), 10000) }
    }

    @Test
    fun 변경_적용은_상태와_onSaleAt을_유지한다() {
        val product = restore(ProductStatus.PAUSED, onSaleAt = now)

        product.applyChange(content(colorSizeOptions()), 12000)

        assertEquals(ProductStatus.PAUSED, product.status)
        assertEquals(now, product.onSaleAt)
        assertEquals(12000, product.originalPrice)
    }

    @Test
    fun 모든_ACTIVE_SKU_재고가_0이면_품절() {
        val product = onSaleWithSkus(
            listOf(
                sku(1, listOf(1, 3), stock = 0),
                sku(2, listOf(1, 4), stock = 5, status = SkuStatus.DISCONTINUED),
            ),
        )
        assertTrue(product.soldOut)

        product.updateSku(SkuId(1), extraPrice = 0, stock = 1, onSale = true)
        assertFalse(product.soldOut)
    }

    @Test
    fun 판매_여부를_끈_SKU는_품절_계산에서_제외된다() {
        val product = onSaleWithSkus(listOf(sku(1, listOf(1, 3), stock = 10, onSale = false)))
        assertTrue(product.soldOut)
    }

    @Test
    fun 재구성_시_같은_조합은_유지_새_조합은_0으로_생성_사라진_조합은_DISCONTINUED() {
        val kept = sku(1, listOf(1, 3), extraPrice = 500, stock = 7)
        val gone = sku(2, listOf(1, 4), stock = 3)
        val product = onSaleWithSkus(listOf(kept, gone))

        product.rebuildSkus(listOf(listOf(1L, 3L), listOf(2L, 3L)))

        assertEquals(3, product.skus.size)
        val keptAfter = product.skus.single { it.combinationKey == "1|3" }
        assertEquals(SkuId(1), keptAfter.id)
        assertEquals(7, keptAfter.stock)
        assertEquals(500, keptAfter.extraPrice)
        assertEquals(SkuStatus.ACTIVE, keptAfter.skuStatus)
        val goneAfter = product.skus.single { it.combinationKey == "1|4" }
        assertEquals(SkuStatus.DISCONTINUED, goneAfter.skuStatus)
        val created = product.skus.single { it.combinationKey == "2|3" }
        assertNull(created.id)
        assertEquals(0, created.stock)
        assertEquals(SkuStatus.ACTIVE, created.skuStatus)
        assertEquals(setOf("1|3", "2|3"), product.activeSkus.map { it.combinationKey }.toSet())
    }

    @Test
    fun DISCONTINUED_조합이_다시_생기면_같은_행이_ACTIVE로_되살아나고_재고는_0이다() {
        val product = onSaleWithSkus(listOf(sku(9, listOf(3, 1), extraPrice = 300, stock = 5, status = SkuStatus.DISCONTINUED)))

        product.rebuildSkus(listOf(listOf(1L, 3L)))

        val revived = product.skus.single()
        assertEquals(SkuId(9), revived.id)
        assertEquals(SkuStatus.ACTIVE, revived.skuStatus)
        assertEquals(0, revived.stock)
        assertEquals(300, revived.extraPrice)
    }

    @Test
    fun 옵션_값_이름만_바꾸면_SKU_ID_재고_추가금액이_유지된다() {
        val product = onSaleWithSkus(combinations().mapIndexed { i, ids -> sku((i + 1).toLong(), ids, extraPrice = 100L * i, stock = i + 1) })
        val renamed = colorSizeOptions().map { opt ->
            opt.copy(values = opt.values.map { v -> if (v.name == "블랙") v.copy(name = "검정") else v })
        }

        product.applyChange(content(renamed), 10000)
        product.rebuildSkus(combinations())

        assertEquals("검정", product.content.options[0].values[0].name)
        assertEquals(4, product.skus.size)
        combinations().forEachIndexed { i, ids ->
            val s = product.skus.single { it.combinationKey == ids.joinToString("|") }
            assertEquals(SkuId((i + 1).toLong()), s.id)
            assertEquals(i + 1, s.stock)
            assertEquals(100L * i, s.extraPrice)
            assertEquals(SkuStatus.ACTIVE, s.skuStatus)
        }
    }

    @Test
    fun 남의_값_ID로_콘텐츠를_바꾸면_PRODUCT_INVALID() {
        val foreign = listOf(ProductOption(1L, "색상", listOf(OptionValue(999L, "블랙"))))
        assertCode(ErrorCode.PRODUCT_INVALID) { restore(ProductStatus.ON_SALE).applyChange(content(foreign), 10000) }

        val rejected = register()
        rejected.reject("x")
        assertCode(ErrorCode.PRODUCT_INVALID) { rejected.editContent(content(foreign)) }
        // 새 값(id null)은 허용된다.
        rejected.editContent(content(listOf(ProductOption(null, "색상", listOf(OptionValue(null, "블랙"))))))
        assertEquals(ProductStatus.PENDING_REVIEW, rejected.status)
    }

    @Test
    fun DISCONTINUED_SKU_갱신은_SKU_NOT_FOUND() {
        val product = onSaleWithSkus(listOf(sku(1, listOf(1, 3), status = SkuStatus.DISCONTINUED)))
        assertCode(ErrorCode.SKU_NOT_FOUND) { product.updateSku(SkuId(1), 0, 1, true) }
        assertCode(ErrorCode.SKU_NOT_FOUND) { product.updateSku(SkuId(77), 0, 1, true) }
    }

    @Test
    fun SKU_갱신_값이_음수면_SKU_INVALID_종료된_상품은_전이_오류() {
        val product = onSaleWithSkus(listOf(sku(1, listOf(1, 3))))
        assertCode(ErrorCode.SKU_INVALID) { product.updateSku(SkuId(1), -1, 1, true) }
        assertCode(ErrorCode.SKU_INVALID) { product.updateSku(SkuId(1), 0, -1, true) }

        val ended = restore(ProductStatus.ENDED, skus = listOf(sku(1, listOf(1, 3))))
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { ended.updateSku(SkuId(1), 0, 1, true) }
    }

    @Test
    fun 종료된_상품의_판매가_변경은_INVALID_STATE_TRANSITION() {
        assertCode(ErrorCode.PRODUCT_INVALID_STATE_TRANSITION) { restore(ProductStatus.ENDED).changeSalePrice(5000) }
    }

    @Test
    fun 판매가_변경으로_정가를_바꿀_수_없고_정가_초과_판매가는_PRODUCT_INVALID_PRICE() {
        val product = restore(ProductStatus.ON_SALE, originalPrice = 10000, salePrice = 9000)

        product.changeSalePrice(8000)
        assertEquals(8000, product.salePrice)
        assertEquals(10000, product.originalPrice)

        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { product.changeSalePrice(10001) }
        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { product.changeSalePrice(0) }
        assertEquals(8000, product.salePrice)
    }

    @Test
    fun 변경_적용_시_새_정가가_현재_판매가보다_작으면_PRODUCT_INVALID_PRICE() {
        val product = restore(ProductStatus.ON_SALE, originalPrice = 10000, salePrice = 9000)
        assertCode(ErrorCode.PRODUCT_INVALID_PRICE) { product.applyChange(content(colorSizeOptions()), 8999) }
        assertEquals(10000, product.originalPrice)
    }

    @Test
    fun 기존_값_ID를_다른_옵션_아래로_옮기면_PRODUCT_INVALID() {
        val product = restore(ProductStatus.PENDING_REVIEW)

        // 값 3(사이즈 M)을 색상 옵션(1) 아래로.
        val moved = content(
            listOf(
                ProductOption(1L, "색상", listOf(OptionValue(1L, "블랙"), OptionValue(3L, "S"))),
                ProductOption(2L, "사이즈", listOf(OptionValue(4L, "M"))),
            ),
        )
        assertCode(ErrorCode.PRODUCT_INVALID) { product.editContent(moved) }

        // 기존 값 ID 를 새 옵션(id null) 아래로.
        val underNew = content(listOf(ProductOption(null, "소재", listOf(OptionValue(1L, "면")))))
        assertCode(ErrorCode.PRODUCT_INVALID) { product.editContent(underNew) }

        // 이 상품에 없는 옵션 ID.
        val unknownOption = content(listOf(ProductOption(99L, "색상", listOf(OptionValue(null, "블랙")))))
        assertCode(ErrorCode.PRODUCT_INVALID) { product.editContent(unknownOption) }

        // applyChange 도 같은 검사를 거친다.
        assertCode(ErrorCode.PRODUCT_INVALID) { restore(ProductStatus.ON_SALE).applyChange(moved, 10000) }
    }

    @Test
    fun 한_요청에_같은_옵션_ID나_값_ID가_두_번_오면_PRODUCT_INVALID() {
        assertCode(ErrorCode.PRODUCT_INVALID) {
            content(
                listOf(
                    ProductOption(1L, "색상", listOf(OptionValue(null, "블랙"))),
                    ProductOption(1L, "사이즈", listOf(OptionValue(null, "M"))),
                ),
            )
        }
        assertCode(ErrorCode.PRODUCT_INVALID) {
            content(
                listOf(
                    ProductOption(1L, "색상", listOf(OptionValue(1L, "블랙"))),
                    ProductOption(2L, "사이즈", listOf(OptionValue(1L, "M"))),
                ),
            )
        }
    }

    @Test
    fun 평균_별점은_후기가_없으면_0점이고_소수_첫째_자리로_반올림한다() {
        fun avg(reviewCount: Long, ratingSum: Long) = Product.restore(
            ProductId(1L), 1L, 2L, content(), 10000, 9000, ProductStatus.ON_SALE, null, now, 0, emptyList(),
            likeCount = 0, reviewCount = reviewCount, ratingSum = ratingSum,
        ).averageRating

        assertEquals(0.0, avg(0, 0))
        assertEquals(3.7, avg(3, 11))
        assertEquals(4.5, avg(2, 9))
        assertEquals(5.0, avg(1, 5))
    }
}
