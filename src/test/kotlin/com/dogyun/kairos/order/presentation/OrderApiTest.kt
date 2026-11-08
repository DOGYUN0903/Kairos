package com.dogyun.kairos.order.presentation

import com.dogyun.kairos.IntegrationTestSupport
import com.dogyun.kairos.deal.FixedClockConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 고정 시각: 2026-12-08T12:00. Mock PG 지연을 줄여 테스트를 빠르게 한다. */
@Import(FixedClockConfig::class)
@TestPropertySource(properties = ["kairos.mock-pg.delay=1ms"])
abstract class OrderApiTestSupport : IntegrationTestSupport() {

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @BeforeTest
    fun cleanUp() {
        jdbcTemplate.update("delete from orders")
        jdbcTemplate.update("delete from payment")
        jdbcTemplate.update("delete from stock")
        jdbcTemplate.update("delete from deal")
    }

    protected fun json(result: MvcResult) = objectMapper.readTree(result.response.contentAsString)

    private fun login(email: String, password: String): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        }.andReturn()
        return json(result).get("data").get("accessToken").asString()
    }

    protected fun adminToken() = login("admin@kairos.test", "test-admin-password")

    protected fun userToken(): String {
        val email = "user-${UUID.randomUUID()}@kairos.com"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password1", "name" to "홍길동"))
        }
        return login(email, "password1")
    }

    /** 진행 중(2026-12-08T10:00 ~ 12-09T10:00) 딜. 인당 제한 2. */
    protected fun createOngoingDeal(totalQuantity: Int = 10, perMemberLimit: Int = 2, openAt: String = "2026-12-08T10:00:00"): Long {
        val result = mockMvc.post("/api/v1/admin/deals") {
            header("Authorization", "Bearer ${adminToken()}")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "productName" to "진행딜",
                    "originalPrice" to 10000,
                    "dealPrice" to 5000,
                    "totalQuantity" to totalQuantity,
                    "perMemberLimit" to perMemberLimit,
                    "openAt" to openAt,
                    "closeAt" to "2026-12-09T10:00:00",
                ),
            )
        }.andExpect { status { isCreated() } }.andReturn()
        return json(result).get("data").get("dealId").asLong()
    }

    protected fun order(token: String?, dealId: Long, quantity: Int) = mockMvc.post("/api/v1/orders") {
        token?.let { header("Authorization", "Bearer $it") }
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("dealId" to dealId, "quantity" to quantity))
    }

    protected fun remaining(dealId: Long): Int =
        json(mockMvc.get("/api/v1/deals/$dealId").andReturn()).get("data").get("remainingQuantity").asInt()
}

class OrderApiTest : OrderApiTestSupport() {

    @Test
    fun 주문하면_201_PAID_남은수량_감소() {
        val dealId = createOngoingDeal(totalQuantity = 10)
        val token = userToken()

        val result = order(token, dealId, 2).andExpect {
            status { isCreated() }
            jsonPath("$.message") { isNotEmpty() }
            jsonPath("$.data.orderId") { isNumber() }
            jsonPath("$.data.status") { value("PAID") }
            jsonPath("$.data.cancelReason") { value(null) }
        }.andReturn()
        val orderId = json(result).get("data").get("orderId").asLong()

        assertEquals(8, remaining(dealId))
        assertEquals("PAID", jdbcTemplate.queryForObject("select order_status from orders where id = ?", String::class.java, orderId))
        assertEquals(10000L, jdbcTemplate.queryForObject("select amount from orders where id = ?", Long::class.java, orderId))
        assertEquals("APPROVED", jdbcTemplate.queryForObject("select payment_status from payment where order_id = ?", String::class.java, orderId))
        // 감사 컬럼: 로그인 회원 id 가 기록된다.
        val memberId = jdbcTemplate.queryForObject("select member_id from orders where id = ?", Long::class.java, orderId)
        assertEquals(memberId, jdbcTemplate.queryForObject("select created_by from orders where id = ?", Long::class.java, orderId))
    }

    @Test
    fun 제한_초과_주문은_409_ORDER_LIMIT_EXCEEDED() {
        val dealId = createOngoingDeal(totalQuantity = 10, perMemberLimit = 2)
        val token = userToken()
        order(token, dealId, 1).andExpect { status { isCreated() } }

        order(token, dealId, 2).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ORDER_LIMIT_EXCEEDED") }
        }
        assertEquals(9, remaining(dealId))
    }

    @Test
    fun 재고_부족이면_409_STOCK_SOLD_OUT() {
        val dealId = createOngoingDeal(totalQuantity = 1, perMemberLimit = 5)

        order(userToken(), dealId, 2).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("STOCK_SOLD_OUT") }
        }
        assertEquals(1, remaining(dealId))
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from orders", Int::class.java))
    }

    @Test
    fun 진행중이_아닌_딜은_409_DEAL_NOT_ONGOING() {
        val dealId = createOngoingDeal(openAt = "2026-12-09T09:00:00")

        order(userToken(), dealId, 1).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DEAL_NOT_ONGOING") }
        }
    }

    @Test
    fun 없는_딜은_404() {
        order(userToken(), 999999L, 1).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("DEAL_NOT_FOUND") }
        }
    }

    @Test
    fun 수량_0은_400() {
        val dealId = createOngoingDeal()
        order(userToken(), dealId, 0).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun 비로그인_주문은_401() {
        val dealId = createOngoingDeal()
        order(null, dealId, 1).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }
}

@TestPropertySource(properties = ["kairos.mock-pg.failure-rate=1"])
class OrderApiPaymentFailureTest : OrderApiTestSupport() {

    @Test
    fun 실패율_1이면_201_CANCELLED_남은수량_원복() {
        val dealId = createOngoingDeal(totalQuantity = 10)

        val result = order(userToken(), dealId, 2).andExpect {
            status { isCreated() }
            jsonPath("$.data.status") { value("CANCELLED") }
            jsonPath("$.data.cancelReason") { value("PAYMENT_FAILED") }
        }.andReturn()
        val orderId = json(result).get("data").get("orderId").asLong()

        assertEquals(10, remaining(dealId))
        assertEquals("CANCELLED", jdbcTemplate.queryForObject("select order_status from orders where id = ?", String::class.java, orderId))
        assertEquals("PAYMENT_FAILED", jdbcTemplate.queryForObject("select cancel_reason from orders where id = ?", String::class.java, orderId))
        assertEquals("FAILED", jdbcTemplate.queryForObject("select payment_status from payment where order_id = ?", String::class.java, orderId))
    }
}
