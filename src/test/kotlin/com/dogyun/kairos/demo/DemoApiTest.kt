package com.dogyun.kairos.demo

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.demo.application.DemoSeeder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ActiveProfiles("test", "demo")
@TestPropertySource(properties = ["kairos.demo.password=$DEMO_PASSWORD"])
class DemoApiTest : CommerceTestSupport() {

    @Autowired
    private lateinit var demoSeeder: DemoSeeder

    /**
     * 기동 시 DemoDataInitializer 가 한 번 시드하지만, 상위 cleanUp 이 매 테스트 전에 카탈로그 테이블을 지운다.
     * 회원은 남으므로 시더를 다시 불러 "기동 직후" 상태로 맞춘다(회원이 이미 있어도 나머지를 채우는 것도 함께 검증된다).
     */
    @BeforeTest
    fun seedAgain() {
        demoSeeder.seed()
    }

    @Test
    fun 기동_시_데모_계정_셋과_승인_판매자_브랜드_카테고리_상품_20개가_있다() {
        demoLogin("buyer@kairos.demo")
        demoLogin("admin@kairos.demo")
        val seller = demoLogin("seller@kairos.demo")
        demoLogin("seller2@kairos.demo")

        assertEquals("APPROVED", json(get("/api/v1/seller/me", seller)).get("data").get("status").asString())
        val products = json(get("/api/v1/products?size=1").andExpect { status { isOk() } }).get("data")
        assertTrue(products.get("totalElements").asLong() >= 20, "판매 중 상품 ${products.get("totalElements")}")
        val categories = json(get("/api/v1/categories").andExpect { status { isOk() } }).get("data")
        assertEquals(6, categories.size())
        assertEquals(3, countMine(seller, "PENDING_REVIEW"))
    }

    @Test
    fun reseed를_두_번_불러도_새로_만드는_수는_두_번째에_전부_0이고_상품_수가_그대로다() {
        val admin = demoLogin("admin@kairos.demo")
        val before = exposedCount()

        reseed(admin)
        val second = reseed(admin)

        for (field in listOf("members", "sellers", "brands", "categories", "products", "pendingReviews")) {
            assertEquals(0, second.get(field).asInt(), field)
        }
        assertEquals(before, exposedCount())
    }

    @Test
    fun 데모_상품_하나를_판매_종료한_뒤_reseed하면_부족분_1개만_새로_만든다() {
        val admin = demoLogin("admin@kairos.demo")
        val seller = demoLogin("seller@kairos.demo")
        val onSale = json(get("/api/v1/seller/products?status=ON_SALE&size=1", seller)).get("data").get("content").get(0)
        post("/api/v1/seller/products/${onSale.get("productId").asLong()}/end", seller).andExpect { status { isOk() } }

        val result = reseed(admin)

        assertEquals(1, result.get("products").asInt())
        assertEquals(0, result.get("pendingReviews").asInt())
        assertEquals(20, countMine(seller, "ON_SALE"))
    }

    @Test
    fun 일반_회원의_reseed는_403() {
        post("/api/v1/demo/reseed", demoLogin("buyer@kairos.demo")).andExpect { status { isForbidden() } }
    }

    @Test
    fun 비로그인_reseed는_401() {
        post("/api/v1/demo/reseed").andExpect { status { isUnauthorized() } }
    }

    private fun reseed(token: String): JsonNode =
        json(post("/api/v1/demo/reseed", token).andExpect { status { isOk() } }).get("data")

    private fun exposedCount(): Long =
        json(get("/api/v1/products?size=1")).get("data").get("totalElements").asLong()

    private fun countMine(sellerToken: String, status: String): Long =
        json(get("/api/v1/seller/products?status=$status&size=1", sellerToken)).get("data").get("totalElements").asLong()

    private fun demoLogin(email: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to DEMO_PASSWORD))
        }.andExpect { status { isOk() } }.andReturn()
        return json(result).get("data").get("accessToken").asString()
    }
}

private const val DEMO_PASSWORD = "demo-password1"
