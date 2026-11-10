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

    @BeforeTest
    fun cleanUp() {
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

    /** items 는 (skuId, quantity) 쌍. 주문 생성 요청을 보낸다. */
    protected fun placeOrder(
        token: String,
        items: List<Pair<Long, Int>>,
        expectedTotal: Long,
        idemKey: String = UUID.randomUUID().toString(),
    ): ResultActionsDsl = postIdem(
        "/api/v1/orders", token,
        mapOf(
            "items" to items.map { mapOf("skuId" to it.first, "quantity" to it.second) },
            "expectedTotalAmount" to expectedTotal,
            "shippingAddress" to address(),
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
    protected fun paidOrder(token: String, items: List<Pair<Long, Int>>, expectedTotal: Long): JsonNode {
        val orderNo = json(placeOrder(token, items, expectedTotal).andExpect { status { isCreated() } }).get("data").get("orderNo").asString()
        val confirmed = json(confirmPayment(token, orderNo, "pay-${UUID.randomUUID()}", expectedTotal).andExpect { status { isOk() } }).get("data")
        val orderId = confirmed.get("orderId").asLong()
        return json(get("/api/v1/orders/$orderId", token).andExpect { status { isOk() } }).get("data")
    }

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
