package com.dogyun.kairos

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.BeforeTest

/** 판매자·카탈로그 API 통합 테스트 공통 도구. 이후 Task 가 cleanUp 대상 테이블을 늘린다. */
abstract class CommerceTestSupport : IntegrationTestSupport() {

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var jwtProvider: com.dogyun.kairos.common.security.JwtProvider

    @Autowired
    protected lateinit var pointService: com.dogyun.kairos.benefit.application.PointService

    @BeforeTest
    fun cleanUp() {
        // C3 테이블은 각 Task 가 만들 때 FK 자식 순서로 이 줄 앞에 추가한다:
        // review_image, review, product_like, point_lot_usage, point_transaction, point_lot, member_point, coupon, coupon_policy
        // (order_item 이 쿠폰·후기를 참조하므로 review → coupon 계열은 order_item 삭제 전에 지운다)
        jdbcTemplate.update("delete from point_lot_usage")
        jdbcTemplate.update("delete from point_transaction")
        jdbcTemplate.update("delete from point_lot")
        jdbcTemplate.update("delete from member_point")
        jdbcTemplate.update("delete from coupon")
        jdbcTemplate.update("delete from coupon_policy")
        jdbcTemplate.update("delete from order_item")
        jdbcTemplate.update("delete from seller_order")
        jdbcTemplate.update("delete from orders")
        jdbcTemplate.update("delete from idempotency_key")
        jdbcTemplate.update("delete from payment")
        jdbcTemplate.update("delete from cart_item")
        jdbcTemplate.update("delete from product_change_request")
        jdbcTemplate.update("delete from product_review_history")
        jdbcTemplate.update("delete from sku")
        jdbcTemplate.update("delete from product_option_value")
        jdbcTemplate.update("delete from product_option")
        jdbcTemplate.update("delete from product_image")
        jdbcTemplate.update("delete from product")
        jdbcTemplate.update("delete from category")
        jdbcTemplate.update("delete from brand_image")
        jdbcTemplate.update("delete from brand")
        jdbcTemplate.update("delete from file")
        jdbcTemplate.update("delete from seller_review_history")
        jdbcTemplate.update("delete from seller")
    }

    protected fun json(result: MvcResult): JsonNode = objectMapper.readTree(result.response.contentAsString)

    protected fun json(result: ResultActionsDsl): JsonNode = json(result.andReturn())

    private fun login(email: String, password: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        }.andReturn()
        return json(result).get("data").get("accessToken").asString()
    }

    protected fun adminToken(): String = login("admin@kairos.test", "test-admin-password")

    /** 랜덤 이메일로 가입하고 로그인한 일반 회원의 액세스 토큰. */
    protected fun newUserToken(): String {
        val email = "user-${UUID.randomUUID()}@kairos.com"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password1", "name" to "홍길동"))
        }
        return login(email, "password1")
    }

    protected fun post(path: String, token: String? = null, body: Any? = null): ResultActionsDsl =
        mockMvc.post(path) { prepare(token, body) }

    protected fun put(path: String, token: String? = null, body: Any? = null): ResultActionsDsl =
        mockMvc.put(path) { prepare(token, body) }

    protected fun patch(path: String, token: String? = null, body: Any? = null): ResultActionsDsl =
        mockMvc.patch(path) { prepare(token, body) }

    protected fun get(path: String, token: String? = null): ResultActionsDsl =
        mockMvc.get(path) { prepare(token, null) }

    protected fun delete(path: String, token: String? = null): ResultActionsDsl =
        mockMvc.delete(path) { prepare(token, null) }

    /** 판매자 신청 → 관리자 승인(수수료율 1000bp)까지 마치고 sellerId 를 돌려준다. */
    protected fun approvedSeller(token: String, businessNumber: String = randomBusinessNumber()): Long {
        val created = post(
            "/api/v1/seller/application", token,
            mapOf(
                "companyName" to "키로스상사",
                "businessNumber" to businessNumber,
                "representativeName" to "홍길동",
                "phone" to "010-1234-5678",
                "bank" to "국민",
                "accountNumber" to "123456-01-123456",
                "holder" to "홍길동",
            ),
        ).andExpect { status { isCreated() } }
        val sellerId = json(created).get("data").get("sellerId").asLong()
        post("/api/v1/admin/sellers/$sellerId/approve", adminToken(), mapOf("commissionRateBp" to 1000))
            .andExpect { status { isOk() } }
        return sellerId
    }

    /** 1x1 PNG 를 업로드하고 fileId 를 돌려준다. */
    protected fun uploadImage(token: String): Long {
        val result = mockMvc.multipart("/api/v1/files/images") {
            file(MockMultipartFile("file", "dot.png", "image/png", PNG_1X1))
            header("Authorization", "Bearer $token")
        }.andExpect { status { isCreated() } }
        return json(result).get("data").get("fileId").asLong()
    }

    /** 브랜드 신청 → 관리자 승인까지 마치고 brandId 를 돌려준다. */
    protected fun approvedBrand(
        sellerToken: String,
        name: String = "브랜드-${UUID.randomUUID().toString().take(8)}",
        logoFileId: Long? = null,
    ): Long {
        val created = post("/api/v1/seller/brands", sellerToken, mapOf("name" to name, "logoFileId" to logoFileId))
            .andExpect { status { isCreated() } }
        val brandId = json(created).get("data").get("brandId").asLong()
        post("/api/v1/admin/brands/$brandId/approve", adminToken()).andExpect { status { isOk() } }
        return brandId
    }

    /** 대분류 하나와 그 아래 소분류를 만들고 소분류 ID 를 돌려준다. */
    protected fun leafCategory(): Long {
        val rootId = createCategory(null, "대분류-${UUID.randomUUID().toString().take(8)}", 1)
        return createCategory(rootId, "소분류-${UUID.randomUUID().toString().take(8)}", 1)
    }

    protected fun createCategory(parentId: Long?, name: String, displayOrder: Int): Long {
        val created = post(
            "/api/v1/admin/categories", adminToken(),
            mapOf("parentId" to parentId, "name" to name, "displayOrder" to displayOrder),
        ).andExpect { status { isCreated() } }
        return json(created).get("data").get("categoryId").asLong()
    }

    /** 기본 옵션: 색상[블랙, 화이트] × 사이즈[M, L] → SKU 4개. 정가 10000, 판매가 9000. */
    protected fun registerProduct(
        sellerToken: String,
        brandId: Long,
        categoryId: Long,
        fileIds: List<Long>,
        options: List<Map<String, Any?>> = colorSizeOptions(),
        originalPrice: Long = 10000,
        salePrice: Long = 9000,
    ): Long {
        val created = post(
            "/api/v1/seller/products", sellerToken,
            mapOf(
                "brandId" to brandId,
                "content" to productContent(categoryId, fileIds, options),
                "originalPrice" to originalPrice,
                "salePrice" to salePrice,
            ),
        ).andExpect { status { isCreated() } }
        return json(created).get("data").get("productId").asLong()
    }

    protected fun productContent(
        categoryId: Long,
        fileIds: List<Long>,
        options: List<Map<String, Any?>> = colorSizeOptions(),
        name: String = "티셔츠",
    ): Map<String, Any?> = mapOf(
        "name" to name,
        "description" to "설명",
        "categoryId" to categoryId,
        "imageFileIds" to fileIds,
        "options" to options,
    )

    protected fun colorSizeOptions(): List<Map<String, Any?>> = listOf(
        mapOf("name" to "색상", "values" to listOf(mapOf("name" to "블랙"), mapOf("name" to "화이트"))),
        mapOf("name" to "사이즈", "values" to listOf(mapOf("name" to "M"), mapOf("name" to "L"))),
    )

    protected fun approveProduct(productId: Long) {
        post("/api/v1/admin/products/$productId/approve", adminToken()).andExpect { status { isOk() } }
    }

    /** 등록 → 관리자 승인 → 모든 SKU 재고를 stock 으로 맞춘 ON_SALE 상품 ID. */
    protected fun onSaleProduct(
        sellerToken: String,
        brandId: Long,
        categoryId: Long,
        fileIds: List<Long>,
        originalPrice: Long = 10000,
        salePrice: Long = 8000,
        stock: Int = 5,
    ): Long {
        val productId = registerProduct(sellerToken, brandId, categoryId, fileIds, originalPrice = originalPrice, salePrice = salePrice)
        approveProduct(productId)
        val skus = sellerProductDetail(sellerToken, productId).get("skus")
        for (i in 0 until skus.size()) {
            patch(
                "/api/v1/seller/products/$productId/skus/${skus.get(i).get("skuId").asLong()}", sellerToken,
                mapOf("extraPrice" to 0, "stock" to stock, "onSale" to true),
            ).andExpect { status { isOk() } }
        }
        return productId
    }

    protected fun sellerProductDetail(token: String, productId: Long): JsonNode =
        json(get("/api/v1/seller/products/$productId", token).andExpect { status { isOk() } }).get("data")

    /** 판매자 상품의 모든 SKU ID (DISCONTINUED 포함). */
    protected fun skuIdsOf(sellerToken: String, productId: Long): List<Long> {
        val skus = sellerProductDetail(sellerToken, productId).get("skus")
        return (0 until skus.size()).map { skus.get(it).get("skuId").asLong() }
    }

    /** 장바구니에 담고 cartItemId 를 돌려준다. */
    protected fun addToCart(token: String, skuId: Long, quantity: Int): Long {
        val result = post("/api/v1/cart/items", token, mapOf("skuId" to skuId, "quantity" to quantity))
            .andExpect { status { isOk() } }
        return json(result).get("data").get("cartItemId").asLong()
    }

    protected fun address(): Map<String, Any?> = mapOf(
        "receiverName" to "홍길동",
        "phone" to "010-1234-5678",
        "zipCode" to "06236",
        "address1" to "서울 강남구 테헤란로 1",
        "address2" to "101호",
        "memo" to null,
    )

    /** `Idempotency-Key` 헤더를 붙여 POST 한다. */
    protected fun postIdem(path: String, token: String?, body: Any?, idemKey: String = UUID.randomUUID().toString()): ResultActionsDsl =
        mockMvc.post(path) {
            prepare(token, body)
            header("Idempotency-Key", idemKey)
        }

    /** items 는 (skuId, quantity), productCoupons 는 (skuId, couponId) 쌍. 주문 생성 요청을 보낸다. */
    protected fun placeOrder(
        token: String,
        items: List<Pair<Long, Int>>,
        expectedTotal: Long,
        idemKey: String = UUID.randomUUID().toString(),
        productCoupons: List<Pair<Long, Long>> = emptyList(),
        cartCouponId: Long? = null,
        pointsToUse: Long = 0,
    ): ResultActionsDsl = postIdem(
        "/api/v1/orders", token,
        mapOf(
            "items" to items.map { mapOf("skuId" to it.first, "quantity" to it.second) },
            "expectedTotalAmount" to expectedTotal,
            "shippingAddress" to address(),
            "productCoupons" to productCoupons.map { mapOf("skuId" to it.first, "couponId" to it.second) },
            "cartCouponId" to cartCouponId,
            "pointsToUse" to pointsToUse,
        ),
        idemKey,
    )

    /** 결제 승인 요청을 보낸다. 승인 응답을 받으려면 paymentKey 에 접두사를 붙이지 않는다(`fail-`, `unknown-` 등은 Mock PG 가 해석한다). */
    protected fun confirmPayment(
        token: String,
        orderNo: String,
        paymentKey: String,
        amount: Long,
        idemKey: String = UUID.randomUUID().toString(),
    ): ResultActionsDsl = postIdem(
        "/api/v1/payments/confirm", token,
        mapOf("orderNo" to orderNo, "paymentKey" to paymentKey, "amount" to amount),
        idemKey,
    )

    /** 주문을 만들고 승인해 PAID 로 만든 뒤 내 주문 상세(`GET /api/v1/orders/{orderId}`)의 data 를 돌려준다. */
    protected fun paidOrder(
        token: String,
        items: List<Pair<Long, Int>>,
        expectedTotal: Long,
        idemKey: String = UUID.randomUUID().toString(),
        productCoupons: List<Pair<Long, Long>> = emptyList(),
        cartCouponId: Long? = null,
        pointsToUse: Long = 0,
    ): JsonNode {
        val orderNo = json(
            placeOrder(token, items, expectedTotal, idemKey, productCoupons, cartCouponId, pointsToUse).andExpect { status { isCreated() } },
        ).get("data").get("orderNo").asString()
        val confirmed = json(confirmPayment(token, orderNo, "pay-${UUID.randomUUID()}", expectedTotal).andExpect { status { isOk() } }).get("data")
        val orderId = confirmed.get("orderId").asLong()
        return json(get("/api/v1/orders/$orderId", token).andExpect { status { isOk() } }).get("data")
    }

    /** 유효 기간은 어제부터 30일 뒤까지. 기본값은 전 상품 대상 2,000원 정액 쿠폰. */
    protected fun couponPolicyBody(
        couponType: String = "PRODUCT",
        targetType: String = "ALL_PRODUCTS",
        discountType: String = "FIXED",
        discountValue: Long = 2_000L,
        maxDiscount: Long? = null,
        minOrderAmount: Long? = null,
        totalQuantity: Int? = null,
        validityDays: Int? = null,
        targetProductId: Long? = null,
    ): Map<String, Any?> {
        val today = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        return mapOf(
            "name" to "테스트 쿠폰",
            "couponType" to couponType,
            "targetType" to targetType,
            "targetProductId" to targetProductId,
            "discountType" to discountType,
            "discountValue" to discountValue,
            "maxDiscount" to maxDiscount,
            "minOrderAmount" to minOrderAmount,
            "totalQuantity" to totalQuantity,
            "validityDays" to validityDays,
            "validFrom" to today.minusDays(1).toString(),
            "validUntil" to today.plusDays(30).toString(),
        )
    }

    /** 관리자(플랫폼) 쿠폰 정책을 만들고 policyId 를 돌려준다. */
    protected fun adminCouponPolicy(body: Map<String, Any?>): Long {
        val created = postIdem("/api/v1/admin/coupons/policies", adminToken(), body).andExpect { status { isCreated() } }
        return json(created).get("data").get("policyId").asLong()
    }

    protected fun sellerCouponPolicy(sellerToken: String, body: Map<String, Any?>): Long {
        val created = postIdem("/api/v1/seller/coupons/policies", sellerToken, body).andExpect { status { isCreated() } }
        return json(created).get("data").get("policyId").asLong()
    }

    /** 쿠폰을 발급받고 couponId 를 돌려준다. */
    protected fun claimCoupon(token: String, policyId: Long): Long {
        val claimed = postIdem("/api/v1/coupons/policies/$policyId/claim", token, null).andExpect { status { isCreated() } }
        return json(claimed).get("data").get("couponId").asLong()
    }

    /** 액세스 토큰의 회원 id. */
    protected fun memberIdOf(token: String): Long = requireNotNull(jwtProvider.parseAccessToken(token)).memberId

    /** 구매 적립 로트를 직접 만든다(원장 ref 는 겹치지 않는 음수 난수). 기본 만료는 365일 뒤. */
    protected fun grantPoints(
        memberId: Long,
        amount: Long,
        expiresAt: java.time.LocalDateTime = java.time.LocalDateTime.now().plusDays(365),
    ) {
        pointService.earn(
            memberId, amount,
            com.dogyun.kairos.benefit.domain.PointTransactionType.EARN_PURCHASE,
            com.dogyun.kairos.benefit.domain.PointSourceType.PURCHASE,
            com.dogyun.kairos.benefit.domain.PointRefType.PURCHASE_CONFIRM,
            -(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)),
            expiresAt,
        )
    }

    protected fun pointBalance(memberId: Long): Long =
        jdbcTemplate.queryForObject("select coalesce(sum(balance), 0) from member_point where member_id = ?", Long::class.java, memberId) ?: 0L

    protected fun lotRemainingSum(memberId: Long): Long =
        jdbcTemplate.queryForObject("select coalesce(sum(remaining_amount), 0) from point_lot where member_id = ?", Long::class.java, memberId) ?: 0L

    /** `GET /api/v1/coupons/me` 기준 쿠폰 ID → 상태(AVAILABLE/USED/EXPIRED). */
    protected fun myCouponStatuses(token: String): Map<Long, String> {
        val content = json(get("/api/v1/coupons/me?size=100", token).andExpect { status { isOk() } }).get("data").get("content")
        return (0 until content.size()).associate { content.get(it).get("couponId").asLong() to content.get(it).get("status").asString() }
    }

    /** 회원의 원장 행 수(transaction_type 이 [type] 인 것). */
    protected fun pointTransactionCount(memberId: Long, type: String): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from point_transaction where member_id = ? and transaction_type = ?", Int::class.java, memberId, type,
        ) ?: 0

    /** 항목 취소(구매자) 응답의 data. */
    protected fun cancelItem(token: String, orderId: Long, itemId: Long, reason: String = "단순 변심"): JsonNode =
        json(
            postIdem("/api/v1/orders/$orderId/items/$itemId/cancel", token, mapOf("reason" to reason)).andExpect { status { isOk() } },
        ).get("data")

    /** 내 주문 상세(`GET /api/v1/orders/{orderId}`)의 data. */
    protected fun orderDetail(token: String, orderId: Long): JsonNode =
        json(get("/api/v1/orders/$orderId", token).andExpect { status { isOk() } }).get("data")

    protected fun suspendSeller(sellerId: Long) {
        post("/api/v1/admin/sellers/$sellerId/suspend", adminToken(), mapOf("reason" to "약관 위반"))
            .andExpect { status { isOk() } }
    }

    protected fun unsuspendSeller(sellerId: Long) {
        post("/api/v1/admin/sellers/$sellerId/unsuspend", adminToken())
            .andExpect { status { isOk() } }
    }

    companion object {
        val PNG_1X1: ByteArray = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAwS2OUAAAAABJRU5ErkJggg==",
        )
    }

    private fun randomBusinessNumber(): String {
        val n = UUID.randomUUID().mostSignificantBits.let { Math.floorMod(it, 1_000_000_000L) }
        return "%03d-%02d-%05d".format(n / 10_000_000, (n / 100_000) % 100, n % 100_000)
    }

    private fun MockHttpServletRequestDsl.prepare(token: String?, body: Any?) {
        token?.let { header("Authorization", "Bearer $it") }
        if (body != null) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }
    }
}
