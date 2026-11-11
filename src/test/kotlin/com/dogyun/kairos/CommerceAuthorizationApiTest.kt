package com.dogyun.kairos

import org.springframework.test.web.servlet.get
import kotlin.test.Test
import kotlin.test.assertEquals

class CommerceAuthorizationApiTest : CommerceTestSupport() {

    private fun applyOnly(token: String) {
        post(
            "/api/v1/seller/application", token,
            mapOf(
                "companyName" to "대기상사", "businessNumber" to "999-88-77665", "representativeName" to "홍길동",
                "phone" to "010-1234-5678", "bank" to "국민", "accountNumber" to "123456-01-123456", "holder" to "홍길동",
            ),
        ).andExpect { status { isCreated() } }
    }

    @Test
    fun 판매자_토큰으로_관리자_API_전부_403() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val body = mapOf("commissionRateBp" to 1000)
        get("/api/v1/admin/sellers", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/sellers/1/approve", sellerToken, body).andExpect { status { isForbidden() } }
        get("/api/v1/admin/brands", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/brands/1/approve", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/products", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/products/1/approve", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/change-requests", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/change-requests/1/approve", sellerToken).andExpect { status { isForbidden() } }
        post("/api/v1/admin/categories", sellerToken, mapOf("parentId" to null, "name" to "x", "displayOrder" to 1))
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun 판매자가_아닌_회원의_판매자_API는_403() {
        val token = newUserToken()
        get("/api/v1/seller/brands", token).andExpect { status { isForbidden() } }
        post("/api/v1/seller/brands", token, mapOf("name" to "브랜드")).andExpect { status { isForbidden() } }
        get("/api/v1/seller/products", token).andExpect { status { isForbidden() } }
        post("/api/v1/seller/products", token, mapOf("brandId" to 1)).andExpect { status { isForbidden() } }
    }

    @Test
    fun PENDING_판매자의_브랜드_신청은_409_SELLER_NOT_ACTIVE() {
        val token = newUserToken()
        applyOnly(token)
        post("/api/v1/seller/brands", token, mapOf("name" to "대기브랜드"))
            .andExpect { status { isConflict() }; jsonPath("$.code") { value("SELLER_NOT_ACTIVE") } }
    }

    @Test
    fun 정지된_판매자는_상품_등록_409_일시중지_200_종료_200() {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val fileId = uploadImage(token)
        val brandId = approvedBrand(token)
        val categoryId = leafCategory()
        val pausable = onSaleProduct(token, brandId, categoryId, listOf(fileId))
        val endable = onSaleProduct(token, brandId, categoryId, listOf(fileId))

        suspendSeller(sellerId)

        post(
            "/api/v1/seller/products", token,
            mapOf(
                "brandId" to brandId, "content" to productContent(categoryId, listOf(fileId)),
                "originalPrice" to 10000, "salePrice" to 9000,
            ),
        ).andExpect { status { isConflict() }; jsonPath("$.code") { value("SELLER_NOT_ACTIVE") } }
        post("/api/v1/seller/products/$pausable/pause", token).andExpect { status { isOk() } }
        post("/api/v1/seller/products/$endable/end", token).andExpect { status { isOk() } }
    }

    @Test
    fun 다른_판매자의_상품_판매가_변경과_SKU_변경은_404() {
        val ownerToken = newUserToken()
        approvedSeller(ownerToken)
        val fileId = uploadImage(ownerToken)
        val productId = onSaleProduct(ownerToken, approvedBrand(ownerToken), leafCategory(), listOf(fileId))
        val skuId = sellerProductDetail(ownerToken, productId).get("skus").get(0).get("skuId").asLong()

        val otherToken = newUserToken()
        approvedSeller(otherToken)
        patch("/api/v1/seller/products/$productId/price", otherToken, mapOf("salePrice" to 7000))
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("PRODUCT_NOT_FOUND") } }
        patch(
            "/api/v1/seller/products/$productId/skus/$skuId", otherToken,
            mapOf("extraPrice" to 0, "stock" to 1, "onSale" to true),
        ).andExpect { status { isNotFound() }; jsonPath("$.code") { value("PRODUCT_NOT_FOUND") } }
    }

    @Test
    fun 다른_회원이_올린_파일을_상품_이미지로_쓰면_400_FILE_NOT_OWNED() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val brandId = approvedBrand(sellerToken)
        val categoryId = leafCategory()
        val othersFile = uploadImage(newUserToken())
        post(
            "/api/v1/seller/products", sellerToken,
            mapOf(
                "brandId" to brandId, "content" to productContent(categoryId, listOf(othersFile)),
                "originalPrice" to 10000, "salePrice" to 9000,
            ),
        ).andExpect { status { isBadRequest() }; jsonPath("$.code") { value("FILE_NOT_OWNED") } }
    }

    @Test
    fun 공개_API는_비로그인으로_200() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val fileId = uploadImage(sellerToken)
        val brandId = approvedBrand(sellerToken)
        val productId = onSaleProduct(sellerToken, brandId, leafCategory(), listOf(fileId))

        get("/api/v1/categories").andExpect { status { isOk() } }
        get("/api/v1/products").andExpect { status { isOk() } }
        get("/api/v1/products/$productId").andExpect { status { isOk() } }
        get("/api/v1/brands/$brandId").andExpect { status { isOk() } }

        val detail = json(get("/api/v1/products/$productId"))
        val imageUrl = detail.get("data").get("imageUrls").get(0).asString()
        val path = imageUrl.substring(imageUrl.indexOf("/files/"))
        assertEquals(200, mockMvc.get(path).andReturn().response.status)
    }

    /** 판매자(sellerToken)의 SKU 하나가 담긴 주문을 구매자가 결제까지 마친 상태로 만든다. 상품 가격 8,000, 수량 1, 배송비 3,000. */
    private fun paidOrderOf(sellerToken: String, buyerToken: String): Triple<Long, Long, Long> {
        val productId = onSaleProduct(sellerToken, approvedBrand(sellerToken), leafCategory(), listOf(uploadImage(sellerToken)))
        val skuId = skuIdsOf(sellerToken, productId).first()
        val order = paidOrder(buyerToken, listOf(skuId to 1), 11000)
        val sellerOrder = order.get("sellerOrders").get(0)
        return Triple(order.get("orderId").asLong(), sellerOrder.get("sellerOrderId").asLong(), sellerOrder.get("items").get(0).get("orderItemId").asLong())
    }

    @Test
    fun 판매자_토큰으로_관리자_주문_API는_403() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        // 실제로 존재하는 다른 회원의 결제 완료 주문으로, 404가 아니라 권한(403) 때문에 막히는지 확인한다.
        val (orderId, _, orderItemId) = paidOrderOf(sellerToken, newUserToken())
        get("/api/v1/admin/orders", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/orders/$orderId", sellerToken).andExpect { status { isForbidden() } }
        postIdem("/api/v1/admin/orders/$orderId/items/$orderItemId/cancel", sellerToken, mapOf("reason" to "x"))
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun 판매자가_아닌_회원의_판매자_주문_API는_403() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val (_, sellerOrderId, orderItemId) = paidOrderOf(sellerToken, newUserToken())

        val token = newUserToken() // 판매자가 아닌 회원
        get("/api/v1/seller/orders", token).andExpect { status { isForbidden() } }
        get("/api/v1/seller/orders/$sellerOrderId", token).andExpect { status { isForbidden() } }
        postIdem("/api/v1/seller/orders/$sellerOrderId/items/$orderItemId/cancel", token, mapOf("reason" to "x"))
            .andExpect { status { isForbidden() } }
        patch("/api/v1/seller/orders/$sellerOrderId/ship", token, mapOf("carrierCode" to "04", "trackingNumber" to "1234567890"))
            .andExpect { status { isForbidden() } }
        patch("/api/v1/seller/orders/$sellerOrderId/deliver", token).andExpect { status { isForbidden() } }
    }

    @Test
    fun 다른_회원의_주문_상세와_항목_취소는_404() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val owner = newUserToken()
        val (orderId, _, orderItemId) = paidOrderOf(sellerToken, owner)

        val other = newUserToken()
        get("/api/v1/orders/$orderId", other)
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("ORDER_NOT_FOUND") } }
        postIdem("/api/v1/orders/$orderId/items/$orderItemId/cancel", other, mapOf("reason" to "변심"))
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("ORDER_NOT_FOUND") } }
    }

    @Test
    fun 다른_판매자의_배송_시작은_404() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val (_, sellerOrderId, _) = paidOrderOf(sellerToken, newUserToken())

        val otherSeller = newUserToken()
        approvedSeller(otherSeller)
        patch(
            "/api/v1/seller/orders/$sellerOrderId/ship", otherSeller,
            mapOf("carrierCode" to "04", "trackingNumber" to "1234567890"),
        ).andExpect { status { isNotFound() }; jsonPath("$.code") { value("SELLER_ORDER_NOT_FOUND") } }
    }

    @Test
    fun 비로그인_장바구니와_주문은_401() {
        get("/api/v1/cart").andExpect { status { isUnauthorized() } }
        post("/api/v1/cart/items", null, mapOf("skuId" to 1, "quantity" to 1)).andExpect { status { isUnauthorized() } }
        get("/api/v1/orders").andExpect { status { isUnauthorized() } }
        get("/api/v1/orders/1").andExpect { status { isUnauthorized() } }
        postIdem("/api/v1/orders", null, mapOf("items" to emptyList<Any>())).andExpect { status { isUnauthorized() } }
        postIdem("/api/v1/payments/confirm", null, mapOf("orderNo" to "x", "paymentKey" to "k", "amount" to 1))
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 판매자_토큰으로_관리자_쿠폰_정책_API는_403() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        postIdem("/api/v1/admin/coupons/policies", sellerToken, couponPolicyBody()).andExpect { status { isForbidden() } }
        get("/api/v1/admin/coupons/policies", sellerToken).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/coupons/policies/1/end", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/members/1/points", sellerToken).andExpect { status { isForbidden() } }
    }

    @Test
    fun 일반_회원의_판매자_쿠폰_정책과_판매자_후기_API는_403() {
        val token = newUserToken()
        postIdem("/api/v1/seller/coupons/policies", token, couponPolicyBody()).andExpect { status { isForbidden() } }
        get("/api/v1/seller/coupons/policies", token).andExpect { status { isForbidden() } }
        patch("/api/v1/seller/coupons/policies/1/end", token).andExpect { status { isForbidden() } }
        get("/api/v1/seller/reviews", token).andExpect { status { isForbidden() } }
        patch("/api/v1/seller/reviews/1/reply", token, mapOf("content" to "감사합니다")).andExpect { status { isForbidden() } }
    }

    @Test
    fun 다른_회원의_후기_수정_삭제는_404() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val owner = newUserToken()
        val productId = onSaleProduct(sellerToken, approvedBrand(sellerToken), leafCategory(), listOf(uploadImage(sellerToken)))
        val skuId = skuIdsOf(sellerToken, productId).first()
        val order = confirmedOrder(owner, sellerToken, listOf(skuId to 1), 11000)
        val orderId = order.get("orderId").asLong()
        val itemId = order.get("sellerOrders").get(0).get("items").get(0).get("orderItemId").asLong()
        val reviewId = writeReview(owner, orderId, itemId)

        val other = newUserToken()
        patch("/api/v1/reviews/$reviewId", other, mapOf("rating" to 1, "content" to "열 글자 이상 되는 수정 본문입니다", "imageFileIds" to emptyList<Long>()))
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("REVIEW_NOT_FOUND") } }
        delete("/api/v1/reviews/$reviewId", other)
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("REVIEW_NOT_FOUND") } }
    }

    @Test
    fun 비로그인_쿠폰_발급_포인트_조회_좋아요_후기_작성은_401() {
        postIdem("/api/v1/coupons/policies/1/claim", null, null).andExpect { status { isUnauthorized() } }
        get("/api/v1/coupons/me").andExpect { status { isUnauthorized() } }
        get("/api/v1/points/me").andExpect { status { isUnauthorized() } }
        get("/api/v1/points/me/transactions").andExpect { status { isUnauthorized() } }
        put("/api/v1/products/1/like").andExpect { status { isUnauthorized() } }
        delete("/api/v1/products/1/like").andExpect { status { isUnauthorized() } }
        get("/api/v1/products/me/likes").andExpect { status { isUnauthorized() } }
        postIdem("/api/v1/orders/1/items/1/reviews", null, mapOf("rating" to 5, "content" to "열 글자 이상 되는 후기 본문입니다"))
            .andExpect { status { isUnauthorized() } }
        get("/api/v1/reviews/me").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 비로그인_상품_후기_목록은_200() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val productId = onSaleProduct(sellerToken, approvedBrand(sellerToken), leafCategory(), listOf(uploadImage(sellerToken)))
        get("/api/v1/products/$productId/reviews").andExpect { status { isOk() } }
    }

    @Test
    fun 비로그인_반품_요청_정산_조회는_401() {
        postIdem("/api/v1/orders/1/items/1/returns", null, mapOf("reason" to "CHANGE_OF_MIND")).andExpect { status { isUnauthorized() } }
        get("/api/v1/returns/me").andExpect { status { isUnauthorized() } }
        get("/api/v1/returns/1").andExpect { status { isUnauthorized() } }
        delete("/api/v1/returns/1").andExpect { status { isUnauthorized() } }
        get("/api/v1/seller/returns").andExpect { status { isUnauthorized() } }
        patch("/api/v1/seller/returns/1/approve").andExpect { status { isUnauthorized() } }
        get("/api/v1/seller/settlements").andExpect { status { isUnauthorized() } }
        get("/api/v1/seller/settlements/1").andExpect { status { isUnauthorized() } }
        get("/api/v1/admin/returns").andExpect { status { isUnauthorized() } }
        get("/api/v1/admin/settlements").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun 일반_회원의_판매자_반품_정산_API는_403() {
        val token = newUserToken() // 판매자가 아닌 회원
        get("/api/v1/seller/returns", token).andExpect { status { isForbidden() } }
        get("/api/v1/seller/returns/1", token).andExpect { status { isForbidden() } }
        patch("/api/v1/seller/returns/1/approve", token).andExpect { status { isForbidden() } }
        patch("/api/v1/seller/returns/1/reject", token, mapOf("rejectReason" to "x")).andExpect { status { isForbidden() } }
        patch("/api/v1/seller/returns/1/receive", token).andExpect { status { isForbidden() } }
        get("/api/v1/seller/settlements", token).andExpect { status { isForbidden() } }
        get("/api/v1/seller/settlements/1", token).andExpect { status { isForbidden() } }
    }

    @Test
    fun 판매자_토큰의_관리자_반품_정산_조정_API는_403() {
        val sellerToken = newUserToken()
        val sellerId = approvedSeller(sellerToken)
        get("/api/v1/admin/returns", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/returns/1", sellerToken).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/returns/1/approve", sellerToken).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/returns/1/receive", sellerToken).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/returns/1/cancel", sellerToken).andExpect { status { isForbidden() } }
        postIdem("/api/v1/admin/settlements/generate", sellerToken, mapOf("periodStart" to "2026-10-05")).andExpect { status { isForbidden() } }
        get("/api/v1/admin/settlements", sellerToken).andExpect { status { isForbidden() } }
        get("/api/v1/admin/settlements/1", sellerToken).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/settlements/1/confirm", sellerToken).andExpect { status { isForbidden() } }
        patch("/api/v1/admin/settlements/1/pay", sellerToken).andExpect { status { isForbidden() } }
        postIdem("/api/v1/admin/sellers/$sellerId/settlement-adjustments", sellerToken, mapOf("amount" to 1000, "reason" to "x"))
            .andExpect { status { isForbidden() } }
        get("/api/v1/admin/sellers/$sellerId/settlement-adjustments", sellerToken).andExpect { status { isForbidden() } }
        delete("/api/v1/admin/settlement-adjustments/1", sellerToken).andExpect { status { isForbidden() } }
    }

    @Test
    fun 다른_회원의_반품_철회는_404() {
        val sellerToken = newUserToken()
        approvedSeller(sellerToken)
        val owner = newUserToken()
        val productId = onSaleProduct(sellerToken, approvedBrand(sellerToken), leafCategory(), listOf(uploadImage(sellerToken)))
        val skuId = skuIdsOf(sellerToken, productId).first()
        val order = deliveredOrder(owner, sellerToken, listOf(skuId to 1), 11000)
        val orderId = order.get("orderId").asLong()
        val itemId = order.get("sellerOrders").get(0).get("items").get(0).get("orderItemId").asLong()
        val returnId = requestReturn(owner, orderId, itemId)

        val other = newUserToken()
        delete("/api/v1/returns/$returnId", other)
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("RETURN_NOT_FOUND") } }
        get("/api/v1/returns/$returnId", other)
            .andExpect { status { isNotFound() }; jsonPath("$.code") { value("RETURN_NOT_FOUND") } }
    }
}
