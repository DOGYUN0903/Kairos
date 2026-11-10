package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.catalog.domain.ProductLikeRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductLikeRepositoryImplTest : CommerceTestSupport() {

    @Autowired
    private lateinit var repository: ProductLikeRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private fun <T : Any> tx(block: () -> T): T = checkNotNull(TransactionTemplate(transactionManager).execute { block() })

    private fun liked(productId: Long, memberId: Long): Int =
        jdbcTemplate.queryForObject("select liked from product_like where product_id = ? and member_id = ?", Int::class.java, productId, memberId)!!

    @Test
    fun like는_신규_true_이미_좋아요_false_취소_후_다시는_true다() {
        assertTrue(tx { repository.like(1L, 7L) })
        assertFalse(tx { repository.like(1L, 7L) })
        assertEquals(1, liked(1L, 7L))

        assertTrue(tx { repository.unlike(1L, 7L) })
        assertEquals(0, liked(1L, 7L))
        assertTrue(tx { repository.like(1L, 7L) })
        assertFalse(tx { repository.like(1L, 7L) })
        assertEquals(1, liked(1L, 7L))
    }

    @Test
    fun unlike는_liked_1일_때만_true이고_행은_회원_상품당_하나다() {
        assertFalse(tx { repository.unlike(1L, 7L) })

        tx { repository.like(1L, 7L) }
        assertTrue(tx { repository.unlike(1L, 7L) })
        assertFalse(tx { repository.unlike(1L, 7L) })
        assertEquals(1L, jdbcTemplate.queryForObject("select count(*) from product_like where product_id = 1 and member_id = 7", Long::class.java))
    }
}
