package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.CommerceTestSupport
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductLikeApiTest : CommerceTestSupport() {

    private fun onSale(): Long {
        val token = newUserToken()
        approvedSeller(token)
        return onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)))
    }

    private fun likedRows(productId: Long): Long =
        jdbcTemplate.queryForObject("select count(*) from product_like where product_id = ? and liked = 1", Long::class.java, productId)!!

    @Test
    fun 같은_회원이_PUT_20회_동시에_눌러도_like_count는_1이다() {
        val productId = onSale()
        val member = newUserToken()
        val pool = Executors.newFixedThreadPool(20)
        try {
            val results = pool.invokeAll(List(20) { Callable { put("/api/v1/products/$productId/like", member).andReturn().response.status } })
            assertTrue(results.all { it.get() == 200 })
        } finally {
            pool.shutdown()
        }

        assertEquals(1L, likeCountOf(productId))
        assertEquals(1L, likedRows(productId))
    }

    @Test
    fun 서로_다른_회원_100명이_동시에_누르면_100이다() {
        val productId = onSale()
        val tokens = List(100) { newUserToken() }
        val pool = Executors.newFixedThreadPool(20)
        try {
            val results = pool.invokeAll(tokens.map { t -> Callable { put("/api/v1/products/$productId/like", t).andReturn().response.status } })
            assertTrue(results.all { it.get() == 200 })
        } finally {
            pool.shutdown()
        }

        assertEquals(100L, likeCountOf(productId))
        assertEquals(100L, likedRows(productId))
    }

    @Test
    fun 동시에_좋아요와_취소를_섞어도_like_count는_실제_좋아요_행_수와_같다() {
        val productId = onSale()
        val tokens = List(10) { newUserToken() }
        val pool = Executors.newFixedThreadPool(20)
        try {
            val tasks = tokens.flatMap { t ->
                List(6) { i ->
                    Callable {
                        if (i % 2 == 0) put("/api/v1/products/$productId/like", t).andReturn().response.status
                        else delete("/api/v1/products/$productId/like", t).andReturn().response.status
                    }
                }
            }
            val results = pool.invokeAll(tasks)
            assertTrue(results.all { it.get() in listOf(200, 204) })
        } finally {
            pool.shutdown()
        }

        assertEquals(likedRows(productId), likeCountOf(productId))
    }

    @Test
    fun DELETE_후_0이고_다시_PUT하면_1이다() {
        val productId = onSale()
        val member = newUserToken()
        likeProduct(member, productId)
        assertEquals(1L, likeCountOf(productId))

        delete("/api/v1/products/$productId/like", member).andExpect { status { isNoContent() } }
        assertEquals(0L, likeCountOf(productId))

        likeProduct(member, productId)
        assertEquals(1L, likeCountOf(productId))
    }

    @Test
    fun PUT_DELETE는_Idempotency_Key_없이_재시도해도_같은_결과다() {
        val productId = onSale()
        val member = newUserToken()

        repeat(3) { put("/api/v1/products/$productId/like", member).andExpect { status { isOk() } } }
        assertEquals(1L, likeCountOf(productId))

        repeat(3) { delete("/api/v1/products/$productId/like", member).andExpect { status { isNoContent() } } }
        assertEquals(0L, likeCountOf(productId))
    }

    @Test
    fun 없는_상품은_404_PRODUCT_NOT_FOUND이고_비로그인은_401이다() {
        val member = newUserToken()
        put("/api/v1/products/999999/like", member).andExpect { status { isNotFound() } }.andExpect { jsonPath("$.code") { value("PRODUCT_NOT_FOUND") } }
        put("/api/v1/products/999999/like").andExpect { status { isUnauthorized() } }
        get("/api/v1/products/me/likes").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 내_좋아요_목록에_판매_종료_상품도_상태와_함께_남는다() {
        val first = onSale()
        val second = onSale()
        val member = newUserToken()
        likeProduct(member, first)
        likeProduct(member, second)
        jdbcTemplate.update("update product set product_status = 'ENDED' where id = ?", first)

        val content = json(get("/api/v1/products/me/likes", member).andExpect { status { isOk() } }).get("data").get("content")
        val byId = (0 until content.size()).associate { content.get(it).get("productId").asLong() to content.get(it) }

        assertEquals(setOf(first, second), byId.keys)
        assertEquals("ENDED", byId.getValue(first).get("productStatus").asString())
        assertEquals("ON_SALE", byId.getValue(second).get("productStatus").asString())
        assertTrue(byId.getValue(second).get("thumbnailUrl").asString().isNotEmpty())
        // 최근 좋아요가 먼저
        assertEquals(second, content.get(0).get("productId").asLong())
    }

    @Test
    fun 취소한_상품은_내_좋아요_목록에서_빠진다() {
        val productId = onSale()
        val member = newUserToken()
        likeProduct(member, productId)
        delete("/api/v1/products/$productId/like", member)

        val content = json(get("/api/v1/products/me/likes", member).andExpect { status { isOk() } }).get("data").get("content")
        assertEquals(0, content.size())
    }

    @Test
    fun 노출되지_않는_상품에_PUT하면_404이고_like_count는_그대로다() {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val brandId = approvedBrand(token)
        val pending = registerProduct(token, brandId, leafCategory(), listOf(uploadImage(token)))
        val paused = onSaleProduct(token, brandId, leafCategory(), listOf(uploadImage(token)))
            .also { post("/api/v1/seller/products/$it/pause", token).andExpect { status { isOk() } } }
        val suspended = onSaleProduct(token, brandId, leafCategory(), listOf(uploadImage(token))).also { suspendSeller(sellerId) }
        val member = newUserToken()

        listOf(pending, paused, suspended).forEach {
            put("/api/v1/products/$it/like", member).andExpect { status { isNotFound() } }.andExpect { jsonPath("$.code") { value("PRODUCT_NOT_FOUND") } }
            assertEquals(0L, likeCountOf(it))
        }
    }

    @Test
    fun 삭제된_상품도_내_좋아요_목록에_DELETED로_남는다() {
        val productId = onSale()
        val member = newUserToken()
        likeProduct(member, productId)
        jdbcTemplate.update("update product set status = 'DELETED' where id = ?", productId)

        val content = json(get("/api/v1/products/me/likes", member).andExpect { status { isOk() } }).get("data").get("content")

        assertEquals(1, content.size())
        assertEquals(productId, content.get(0).get("productId").asLong())
        assertEquals("DELETED", content.get(0).get("productStatus").asString())
    }

    @Test
    fun 삭제된_상품도_DELETE로_좋아요를_취소하면_204이고_내_좋아요_목록에서_사라진다() {
        val productId = onSale()
        val member = newUserToken()
        likeProduct(member, productId)
        jdbcTemplate.update("update product set status = 'DELETED' where id = ?", productId)

        delete("/api/v1/products/$productId/like", member).andExpect { status { isNoContent() } }

        val content = json(get("/api/v1/products/me/likes", member).andExpect { status { isOk() } }).get("data").get("content")
        assertEquals(0, content.size())
        assertEquals(0L, likeCountOf(productId))
    }

    @Test
    fun 좋아요한_적_없거나_없는_상품의_DELETE는_204이고_like_count는_그대로다() {
        val productId = onSale()
        val other = newUserToken()
        likeProduct(other, productId)
        val member = newUserToken()

        delete("/api/v1/products/$productId/like", member).andExpect { status { isNoContent() } }
        delete("/api/v1/products/999999/like", member).andExpect { status { isNoContent() } }

        assertEquals(1L, likeCountOf(productId))
    }
}
