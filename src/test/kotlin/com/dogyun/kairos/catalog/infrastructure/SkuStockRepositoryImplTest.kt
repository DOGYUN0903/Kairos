package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.catalog.application.SkuQuantity
import com.dogyun.kairos.catalog.application.SkuStockService
import com.dogyun.kairos.catalog.domain.SkuStockRepository
import com.dogyun.kairos.common.domain.DomainException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkuStockRepositoryImplTest : CommerceTestSupport() {

    @Autowired
    private lateinit var repository: SkuStockRepository

    @Autowired
    private lateinit var stockService: SkuStockService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    /** 저장소는 서비스 트랜잭션 안에서 쓰이므로(@Modifying 쿼리는 트랜잭션 필수) 호출마다 트랜잭션을 연다. */
    private fun <T : Any> tx(block: () -> T): T = checkNotNull(TransactionTemplate(transactionManager).execute { block() })

    /** 재고 5 인 SKU 4개짜리 ON_SALE 상품을 만들고 (productId, 첫 SKU ID) 를 돌려준다. */
    private fun product(): Pair<Long, Long> {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val brandId = approvedBrand(sellerToken)
        val productId = onSaleProduct(sellerToken, brandId, leafCategory(), listOf(uploadImage(sellerToken)), stock = 5)
        val skuId = jdbcTemplate.queryForObject("select min(id) from sku where product_id = ?", Long::class.java, productId)!!
        return productId to skuId
    }

    private fun stock(skuId: Long): Int = jdbcTemplate.queryForObject("select stock from sku where id = ?", Int::class.java, skuId)!!

    @Test
    fun 재고_5에서_3을_빼면_true이고_2가_남는다() {
        val (_, skuId) = product()

        assertTrue(tx { repository.decreaseIfEnough(skuId, 3) })

        assertEquals(2, stock(skuId))
    }

    @Test
    fun 재고보다_많이_빼면_false이고_재고는_그대로다() {
        val (_, skuId) = product()

        assertFalse(tx { repository.decreaseIfEnough(skuId, 6) })

        assertEquals(5, stock(skuId))
    }

    @Test
    fun 동시에_10번_1씩_빼면_정확히_5번만_성공하고_재고는_0이다() {
        val (_, skuId) = product()
        val pool = Executors.newFixedThreadPool(10)
        val ready = CountDownLatch(10)
        val start = CountDownLatch(1)
        val success = AtomicInteger()
        val soldOut = AtomicInteger()

        val futures = (1..10).map {
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    stockService.decrease(listOf(SkuQuantity(skuId, 1)))
                    success.incrementAndGet()
                } catch (e: DomainException) {
                    soldOut.incrementAndGet()
                }
            }
        }
        ready.await()
        start.countDown()
        futures.forEach { it.get() }
        pool.shutdown()

        assertEquals(5, success.get())
        assertEquals(5, soldOut.get())
        assertEquals(0, stock(skuId))
    }

    @Test
    fun DISCONTINUED_SKU도_increase된다() {
        val (_, skuId) = product()
        jdbcTemplate.update("update sku set sku_status = 'DISCONTINUED' where id = ?", skuId)

        tx { repository.increase(skuId, 2) }

        assertEquals(7, stock(skuId))
    }

    @Test
    fun addSalesCount는_음수_delta로_줄인다() {
        val (productId, _) = product()
        tx { repository.addSalesCount(productId, 5) }
        tx { repository.addSalesCount(productId, -2) }

        val sales = jdbcTemplate.queryForObject("select sales_count from product where id = ?", Long::class.java, productId)
        assertEquals(3L, sales)
    }
}
