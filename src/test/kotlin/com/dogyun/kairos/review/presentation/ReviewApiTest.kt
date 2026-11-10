package com.dogyun.kairos.review.presentation

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import com.dogyun.kairos.review.application.ReviewService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReviewApiTest : CommerceTestSupport() {

    @Autowired
    private lateinit var reviewService: ReviewService

    private class Shop(val sellerToken: String, val sellerId: Long, val productId: Long, val skuIds: List<Long>)

    private fun shop(): Shop {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val productId = onSaleProduct(token, approvedBrand(token), leafCategory(), listOf(uploadImage(token)), originalPrice = 20_000, salePrice = 20_000, stock = 5)
        return Shop(token, sellerId, productId, skuIdsOf(token, productId).sorted())
    }

    private class Item(val orderId: Long, val orderItemId: Long)

    /** [count] 개 SKU 를 하나씩 담은 확정 주문의 항목들(항목 id 오름차순). 20,000 × count, 50,000 미만이면 배송비 3,000. */
    private fun confirmedItems(user: String, shop: Shop, count: Int = 1): List<Item> {
        val amount = 20_000L * count
        val order = confirmedOrder(user, shop.sellerToken, shop.skuIds.take(count).map { it to 1 }, if (amount >= 50_000) amount else amount + 3_000)
        return items(order)
    }

    private fun items(order: JsonNode): List<Item> {
        val items = order.get("sellerOrders").get(0).get("items")
        return (0 until items.size()).map { Item(order.get("orderId").asLong(), items.get(it).get("orderItemId").asLong()) }.sortedBy { it.orderItemId }
    }

    private fun write(user: String, item: Item, rating: Int = 5, imageFileIds: List<Long> = emptyList(), idemKey: String = UUID.randomUUID().toString()) =
        postIdem(
            "/api/v1/orders/${item.orderId}/items/${item.orderItemId}/reviews", user,
            mapOf("rating" to rating, "content" to "열 글자 이상 되는 후기 본문입니다", "imageFileIds" to imageFileIds),
            idemKey,
        )

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect {
            status { isEqualTo(httpStatus) }
            jsonPath("$.code") { value(code) }
        }

    private fun reviewLedgerSum(memberId: Long): Long =
        jdbcTemplate.queryForObject(
            "select coalesce(sum(amount), 0) from point_transaction where member_id = ? and transaction_type = 'EARN_REVIEW'",
            Long::class.java, memberId,
        ) ?: 0L

    private fun productReviews(productId: Long): JsonNode =
        json(get("/api/v1/products/$productId/reviews").andExpect { status { isOk() } }).get("data").get("content")

    @Test
    fun 확정_항목에_후기를_쓰면_201이고_상품_상세의_reviewCount_averageRating과_포인트_100이_맞다() {
        val shop = shop()
        val user = newUserToken()
        val item = confirmedItems(user, shop).single()

        write(user, item, rating = 4).andExpect {
            status { isCreated() }
            jsonPath("$.data.rewardPoints") { value(100) }
        }

        val detail = json(get("/api/v1/products/${shop.productId}").andExpect { status { isOk() } }).get("data")
        assertEquals(1L, detail.get("reviewCount").asLong())
        assertEquals(4.0, detail.get("averageRating").asDouble())
        assertEquals(100L, reviewLedgerSum(memberIdOf(user)))
        assertEquals(Triple(1L, 4L, 0L), productStats(shop.productId))
    }

    @Test
    fun 사진_후기는_500P가_한_번만_적립된다() {
        val shop = shop()
        val user = newUserToken()
        val item = confirmedItems(user, shop).single()
        val fileId = uploadImage(user)
        val key = UUID.randomUUID().toString()

        val first = json(write(user, item, imageFileIds = listOf(fileId), idemKey = key).andExpect { status { isCreated() } }).get("data")
        val second = json(write(user, item, imageFileIds = listOf(fileId), idemKey = key).andExpect { status { isCreated() } }).get("data")

        assertEquals(500L, first.get("rewardPoints").asLong())
        assertEquals(first.get("reviewId").asLong(), second.get("reviewId").asLong())
        assertEquals(500L, reviewLedgerSum(memberIdOf(user)))
        assertEquals(1, pointTransactionCount(memberIdOf(user), "EARN_REVIEW"))
        assertEquals(1, productReviews(shop.productId).get(0).get("imageUrls").size())
        assertEquals(Triple(1L, 5L, 0L), productStats(shop.productId))
    }

    @Test
    fun 배송_중_항목은_409_REVIEW_NOT_ELIGIBLE() {
        val shop = shop()
        val user = newUserToken()
        val order = paidOrder(user, listOf(shop.skuIds[0] to 1), 23_000)
        val soId = order.get("sellerOrders").get(0).get("sellerOrderId").asLong()
        patch("/api/v1/seller/orders/$soId/ship", shop.sellerToken, mapOf("carrierCode" to "04", "trackingNumber" to "T-1"))
            .andExpect { status { isOk() } }

        write(user, items(order).single()).expectCode(409, "REVIEW_NOT_ELIGIBLE")
        write(newUserToken(), items(order).single()).expectCode(404, "ORDER_ITEM_NOT_FOUND")
        assertEquals(Triple(0L, 0L, 0L), productStats(shop.productId))
    }

    @Test
    fun 같은_항목_두_번은_다른_멱등_키여도_409_REVIEW_ALREADY_WRITTEN() {
        val shop = shop()
        val user = newUserToken()
        val item = confirmedItems(user, shop).single()

        write(user, item).andExpect { status { isCreated() } }
        write(user, item).expectCode(409, "REVIEW_ALREADY_WRITTEN")

        assertEquals(100L, reviewLedgerSum(memberIdOf(user)))
        assertEquals(Triple(1L, 5L, 0L), productStats(shop.productId))
    }

    @Test
    fun 삭제한_후기는_목록에서_빠지고_수정_답글은_REVIEW_NOT_FOUND이며_집계는_한_번만_준다() {
        val shop = shop()
        val user = newUserToken()
        val item = confirmedItems(user, shop).single()
        val reviewId = writeReview(user, item.orderId, item.orderItemId, rating = 4)
        assertEquals(1, productReviews(shop.productId).size())

        delete("/api/v1/reviews/$reviewId", user).andExpect { status { isNoContent() } }
        delete("/api/v1/reviews/$reviewId", user).expectCode(404, "REVIEW_NOT_FOUND")

        assertEquals(0, productReviews(shop.productId).size())
        assertEquals(0, json(get("/api/v1/reviews/me", user).andExpect { status { isOk() } }).get("data").get("content").size())
        patch("/api/v1/reviews/$reviewId", user, mapOf("rating" to 1, "content" to "고쳐 쓴 열 글자 이상 본문"))
            .expectCode(404, "REVIEW_NOT_FOUND")
        val replyError = assertFailsWith<DomainException> { reviewService.reply(shop.sellerId, reviewId, "감사합니다") }
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, replyError.errorCode)
        write(user, item).expectCode(409, "REVIEW_ALREADY_WRITTEN")

        assertEquals(Triple(0L, 0L, 0L), productStats(shop.productId))
        assertEquals(100L, reviewLedgerSum(memberIdOf(user)))
    }

    @Test
    fun 수정_삭제_뒤_rating_sum과_review_count가_실제_행과_같다() {
        val shop = shop()
        val user = newUserToken()
        val items = confirmedItems(user, shop, count = 3)
        val ids = items.zip(listOf(5, 3, 1)).map { (item, rating) -> writeReview(user, item.orderId, item.orderItemId, rating) }

        patch("/api/v1/reviews/${ids[1]}", user, mapOf("rating" to 4, "content" to "별점을 바꾼 열 글자 이상 본문", "imageFileIds" to listOf(uploadImage(user))))
            .andExpect {
                status { isOk() }
                jsonPath("$.data.rating") { value(4) }
                jsonPath("$.data.rewardPoints") { value(100) }
            }
        delete("/api/v1/reviews/${ids[2]}", user).andExpect { status { isNoContent() } }

        val actual = jdbcTemplate.queryForObject(
            "select count(*), coalesce(sum(rating), 0) from review where product_id = ? and status = 'ACTIVE'",
            { rs, _ -> rs.getLong(1) to rs.getLong(2) },
            shop.productId,
        )!!
        val stats = productStats(shop.productId)
        assertEquals(2L to 9L, actual)
        assertEquals(actual, stats.first to stats.second)
        assertEquals(300L, reviewLedgerSum(memberIdOf(user)))
        assertEquals(2, json(get("/api/v1/reviews/me", user)).get("data").get("content").size())
    }

    @Test
    fun 사진_2장을_다른_사진_1장으로_수정하면_이전_사진은_DELETED이고_적립은_그대로다() {
        val shop = shop()
        val user = newUserToken()
        val item = confirmedItems(user, shop).single()
        val (a, b, c) = listOf(uploadImage(user), uploadImage(user), uploadImage(user))
        val reviewId = writeReview(user, item.orderId, item.orderItemId, rating = 5, imageFileIds = listOf(a, b))
        val memberId = memberIdOf(user)
        val balanceBefore = pointBalance(memberId)

        val edited = json(
            patch("/api/v1/reviews/$reviewId", user, mapOf("rating" to 5, "content" to "사진을 바꾼 열 글자 이상 본문", "imageFileIds" to listOf(c)))
                .andExpect { status { isOk() }; jsonPath("$.data.rewardPoints") { value(500) } },
        ).get("data")

        assertEquals(1, edited.get("imageUrls").size())
        val rows = jdbcTemplate.query(
            "select file_id, status from review_image where review_id = ? order by id",
            { rs, _ -> rs.getLong(1) to rs.getString(2) }, reviewId,
        )
        assertEquals(listOf(a to "DELETED", b to "DELETED", c to "ACTIVE"), rows)
        assertEquals(1, productReviews(shop.productId).get(0).get("imageUrls").size())
        assertEquals(balanceBefore, pointBalance(memberId))
        assertEquals(500L, reviewLedgerSum(memberId))
        assertEquals(1, pointTransactionCount(memberId, "EARN_REVIEW"))
    }

    @Test
    fun 작성자_이름은_마스킹된다() {
        val shop = shop()
        val user = newUserToken(name = "김철수")
        val item = confirmedItems(user, shop).single()
        writeReview(user, item.orderId, item.orderItemId)

        val review = productReviews(shop.productId).get(0)
        assertEquals("김**", review.get("writerName").asString())
        assertEquals("티셔츠", review.get("productName").asString())
    }
}
