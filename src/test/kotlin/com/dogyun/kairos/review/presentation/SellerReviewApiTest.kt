package com.dogyun.kairos.review.presentation

import com.dogyun.kairos.CommerceTestSupport
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SellerReviewApiTest : CommerceTestSupport() {

    private class Shop(val sellerToken: String, val skuId: Long)

    private fun shop(): Shop {
        val token = newUserToken()
        approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = 20_000, salePrice = 20_000, stock = 5)
        return Shop(token, skuIdsOf(token, productId).sorted()[0])
    }

    /** 확정 주문을 만들고 후기를 써서 reviewId 를 돌려준다. */
    private fun reviewOf(shop: Shop, buyer: String): Long {
        val order = confirmedOrder(buyer, shop.sellerToken, listOf(shop.skuId to 1), 23_000)
        val itemId = order.get("sellerOrders").get(0).get("items").get(0).get("orderItemId").asLong()
        return writeReview(buyer, order.get("orderId").asLong(), itemId)
    }

    private fun sellerReviews(token: String): JsonNode =
        json(get("/api/v1/seller/reviews", token).andExpect { status { isOk() } }).get("data").get("content")

    private fun reply(token: String, reviewId: Long, content: String) =
        patch("/api/v1/seller/reviews/$reviewId/reply", token, mapOf("content" to content))

    @Test
    fun 판매자는_자기_상품_후기만_보고_답글을_달면_replyContent가_보인다() {
        val shop = shop()
        val other = shop()
        val buyer = newUserToken()
        val reviewId = reviewOf(shop, buyer)
        reviewOf(other, newUserToken())

        val list = sellerReviews(shop.sellerToken)
        assertEquals(1, list.size())
        assertEquals(reviewId, list.get(0).get("reviewId").asLong())
        assertNull(list.get(0).get("replyContent")?.takeIf { !it.isNull })

        reply(shop.sellerToken, reviewId, "구매해 주셔서 감사합니다").andExpect {
            status { isOk() }
            jsonPath("$.data.replyContent") { value("구매해 주셔서 감사합니다") }
        }

        assertEquals("구매해 주셔서 감사합니다", sellerReviews(shop.sellerToken).get(0).get("replyContent").asString())
        val mine = json(get("/api/v1/reviews/me", buyer).andExpect { status { isOk() } }).get("data").get("content")
        assertEquals("구매해 주셔서 감사합니다", mine.get(0).get("replyContent").asString())
    }

    @Test
    fun 같은_답글_PATCH_두_번은_200이고_내용은_마지막_것이다() {
        val shop = shop()
        val reviewId = reviewOf(shop, newUserToken())

        reply(shop.sellerToken, reviewId, "첫 답글").andExpect { status { isOk() } }
        reply(shop.sellerToken, reviewId, "첫 답글").andExpect { status { isOk() } }
        reply(shop.sellerToken, reviewId, "고친 답글").andExpect { status { isOk() } }

        assertEquals("고친 답글", sellerReviews(shop.sellerToken).get(0).get("replyContent").asString())
        reply(shop.sellerToken, reviewId, "   ").andExpect { status { isBadRequest() } }
    }

    @Test
    fun 다른_판매자_후기_답글은_404() {
        val shop = shop()
        val other = shop()
        val reviewId = reviewOf(shop, newUserToken())

        reply(other.sellerToken, reviewId, "남의 후기 답글").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("REVIEW_NOT_FOUND") }
        }
        reply(shop.sellerToken, 999_999_999L, "없는 후기").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("REVIEW_NOT_FOUND") }
        }
        assertEquals(0, sellerReviews(other.sellerToken).size())
    }

    @Test
    fun 일반_회원은_403() {
        val shop = shop()
        val reviewId = reviewOf(shop, newUserToken())
        val member = newUserToken()

        get("/api/v1/seller/reviews", member).andExpect { status { isForbidden() } }
        reply(member, reviewId, "답글").andExpect { status { isForbidden() } }
        get("/api/v1/seller/reviews").andExpect { status { isUnauthorized() } }
    }
}
