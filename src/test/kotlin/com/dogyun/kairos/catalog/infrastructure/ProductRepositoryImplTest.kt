package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.IntegrationTestSupport
import com.dogyun.kairos.catalog.domain.OptionValue
import com.dogyun.kairos.catalog.domain.Product
import com.dogyun.kairos.catalog.domain.ProductContent
import com.dogyun.kairos.catalog.domain.ProductId
import com.dogyun.kairos.catalog.domain.ProductOption
import com.dogyun.kairos.catalog.domain.ProductRepository
import com.dogyun.kairos.catalog.domain.SkuStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 저장소는 서비스 트랜잭션 안에서 쓰이므로 테스트도 호출마다 트랜잭션을 연다(LAZY 연관 접근 때문). */
class ProductRepositoryImplTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var repository: ProductRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Suppress("UNCHECKED_CAST")
    private fun <T> tx(block: () -> T): T = TransactionTemplate(transactionManager).execute { block() } as T

    @BeforeTest
    fun cleanUp() {
        listOf("sku", "product_option_value", "product_option", "product_image", "product").forEach {
            jdbcTemplate.update("delete from $it")
        }
    }

    private fun colorSize() = listOf(
        ProductOption(null, "색상", listOf(OptionValue(null, "블랙"), OptionValue(null, "화이트"))),
        ProductOption(null, "사이즈", listOf(OptionValue(null, "M"), OptionValue(null, "L"))),
    )

    private fun register(options: List<ProductOption> = colorSize(), imageFileIds: List<Long> = listOf(30L, 10L, 20L)) =
        Product.register(1L, 2L, ProductContent("티셔츠", "설명", 5L, imageFileIds, options), 10000, 9000)

    private fun count(sql: String, vararg args: Any): Int = jdbcTemplate.queryForObject(sql, Int::class.java, *args)!!

    private fun edit(productId: ProductId, content: ProductContent): Product = tx {
        val p = repository.findById(productId)!!
        p.editContent(content)
        repository.save(p)
    }

    @Test
    fun 판매자_저장은_판매량을_덮어쓰지_않는다() {
        val productId = assertNotNull(tx { repository.save(register()) }.id)
        val stale = tx { repository.findById(productId)!! }
        jdbcTemplate.update("update product set sales_count = 7 where id = ?", productId.value)

        tx { repository.save(stale.also { it.changeSalePrice(8000) }) }

        assertEquals(7L, jdbcTemplate.queryForObject("select sales_count from product where id = ?", Long::class.java, productId.value))
        assertEquals(8000L, jdbcTemplate.queryForObject("select sale_price from product where id = ?", Long::class.java, productId.value))
    }

    @Test
    fun 저장_후_읽으면_이미지_순서_옵션_값_ID_SKU가_그대로다() {
        val saved = tx { repository.save(register()) }
        val productId = assertNotNull(saved.id)

        val found = assertNotNull(tx { repository.findById(productId) })

        assertEquals(listOf(30L, 10L, 20L), found.content.imageFileIds)
        assertEquals(listOf("색상", "사이즈"), found.content.options.map { it.name })
        assertEquals(listOf("블랙", "화이트"), found.content.options[0].values.map { it.name })
        assertEquals(listOf("M", "L"), found.content.options[1].values.map { it.name })
        val valueIds = found.content.options.flatMap { it.values }.map { assertNotNull(it.id) }
        assertEquals(4, valueIds.toSet().size)
        assertEquals(4, found.skus.size)
        assertTrue(found.skus.all { it.id != null && it.skuStatus == SkuStatus.ACTIVE && it.stock == 0 && it.onSale })
        val (black, white) = found.content.options[0].values.map { it.id!! }
        val (m, l) = found.content.options[1].values.map { it.id!! }
        assertEquals(
            setOf(listOf(black, m), listOf(black, l), listOf(white, m), listOf(white, l)).map { it.sorted() }.toSet(),
            found.skus.map { it.optionValueIds }.toSet(),
        )
        assertEquals(1, count("select count(*) from product_image where product_id = ? and is_main = 1 and file_id = 30 and display_order = 0", productId.value))
        assertEquals(1, count("select count(*) from sku where option_value_ids = ?", listOf(black, m).sorted().joinToString("|")))
        assertEquals(productId, tx { repository.findByIdAndSellerId(productId, 1L) }?.id)
        assertNull(tx { repository.findByIdAndSellerId(productId, 99L) })
        assertTrue(repository.existsByCategoryId(5L))
        assertEquals(1, tx { repository.findPageBySellerId(1L, null, 0, 20) }.totalElements)
        assertEquals(0, tx { repository.findPageBySellerId(2L, null, 0, 20) }.totalElements)
    }

    @Test
    fun 옵션_없는_상품은_빈_조합_SKU_하나가_생긴다() {
        val saved = tx { repository.save(register(options = emptyList())) }

        assertEquals(1, saved.skus.size)
        assertEquals(emptyList(), saved.skus.single().optionValueIds)
        assertEquals(1, count("select count(*) from sku where product_id = ? and option_value_ids = ''", saved.id!!.value))
    }

    @Test
    fun 콘텐츠_변경_저장_시_사라진_조합은_DISCONTINUED로_남고_유지된_SKU_ID는_같다() {
        val saved = tx { repository.save(register()) }
        val productId = assertNotNull(saved.id)
        val color = saved.content.options[0]
        val size = saved.content.options[1]
        val (black, white) = color.values
        val (m, l) = size.values
        val blackM = saved.skus.single { it.optionValueIds == listOf(black.id!!, m.id!!).sorted() }
        val whiteL = saved.skus.single { it.optionValueIds == listOf(white.id!!, l.id!!).sorted() }
        tx {
            val p = repository.findById(productId)!!
            p.updateSku(blackM.id!!, 500, 3, true)
            p.updateSku(whiteL.id!!, 0, 7, true)
            repository.save(p)
        }

        // 블랙 → 검정(ID 유지), 화이트 삭제, 레드 추가; 이미지 순서 변경.
        val changed = edit(
            productId,
            ProductContent(
                "티셔츠", "설명", 5L, listOf(10L, 30L),
                listOf(
                    ProductOption(color.id, "색상", listOf(OptionValue(black.id, "검정"), OptionValue(null, "레드"))),
                    ProductOption(size.id, "사이즈", listOf(OptionValue(m.id, "M"), OptionValue(l.id, "L"))),
                ),
            ),
        )

        assertEquals(listOf(10L, 30L), changed.content.imageFileIds)
        assertEquals(listOf("검정", "레드"), changed.content.options[0].values.map { it.name })
        assertEquals(black.id, changed.content.options[0].values[0].id)
        val keptBlackM = changed.skus.single { it.id == blackM.id }
        assertEquals(3, keptBlackM.stock)
        assertEquals(500L, keptBlackM.extraPrice)
        assertEquals(SkuStatus.ACTIVE, keptBlackM.skuStatus)
        val droppedWhiteL = changed.skus.single { it.id == whiteL.id }
        assertEquals(SkuStatus.DISCONTINUED, droppedWhiteL.skuStatus)
        assertEquals(7, droppedWhiteL.stock)
        assertEquals(4, changed.skus.count { it.skuStatus == SkuStatus.ACTIVE })
        assertEquals(6, changed.skus.size)
        assertEquals(6, count("select count(*) from sku where product_id = ?", productId.value))
        assertEquals(2, count("select count(*) from sku where product_id = ? and sku_status = 'DISCONTINUED'", productId.value))
        assertEquals(0, count("select count(*) from sku where status = 'DELETED'"))
        assertEquals(1, count("select count(*) from product_option_value where id = ? and status = 'DELETED'", white.id!!))
        assertEquals(3, count("select count(*) from product_image where product_id = ? and status = 'DELETED'", productId.value))
        assertEquals(2, count("select count(*) from product_image where product_id = ? and status = 'ACTIVE'", productId.value))

        // 옵션 축 하나를 통째로 빼면 그 옵션·값은 소프트 딜리트되고 모든 기존 조합은 DISCONTINUED, 새 조합은 insert.
        val single = edit(
            productId,
            ProductContent(
                "티셔츠", "설명", 5L, listOf(10L, 30L),
                listOf(ProductOption(color.id, "색상", listOf(OptionValue(black.id, "검정"), OptionValue(changed.content.options[0].values[1].id, "레드")))),
            ),
        )
        assertEquals(2, single.skus.count { it.skuStatus == SkuStatus.ACTIVE })
        assertEquals(6, single.skus.count { it.skuStatus == SkuStatus.DISCONTINUED })
        assertEquals(1, count("select count(*) from product_option where id = ? and status = 'DELETED'", size.id!!))
        assertEquals(2, count("select count(*) from product_option_value where option_id = ? and status = 'DELETED'", size.id!!))
        assertEquals(0, count("select count(*) from sku where status = 'DELETED'"))
    }
}
