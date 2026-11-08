package com.dogyun.kairos.deal.presentation

import com.dogyun.kairos.IntegrationTestSupport
import com.dogyun.kairos.deal.FixedClockConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(FixedClockConfig::class)
class DealApiTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // 고정 시각: 2026-12-08T12:00
    private val scheduled = Triple("2026-12-09T10:00:00", "2026-12-10T10:00:00", "예정딜")
    private val ongoing = Triple("2026-12-08T10:00:00", "2026-12-09T10:00:00", "진행딜")
    private val closed = Triple("2026-12-07T10:00:00", "2026-12-08T11:00:00", "종료딜")

    @BeforeTest
    fun cleanUp() {
        jdbcTemplate.update("delete from stock")
        jdbcTemplate.update("delete from deal")
    }

    private fun json(result: MvcResult) = objectMapper.readTree(result.response.contentAsString)

    private fun adminToken(): String {
        val result = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to "admin@kairos.test", "password" to "test-admin-password"))
        }.andReturn()
        return json(result).get("data").get("accessToken").asString()
    }

    private fun dealBody(
        times: Triple<String, String, String> = scheduled,
        totalQuantity: Int = 10,
        originalPrice: Long = 10000,
        dealPrice: Long = 5000,
    ) = objectMapper.writeValueAsString(
        mapOf(
            "productName" to times.third,
            "originalPrice" to originalPrice,
            "dealPrice" to dealPrice,
            "totalQuantity" to totalQuantity,
            "perMemberLimit" to 2,
            "openAt" to times.first,
            "closeAt" to times.second,
        ),
    )

    private fun createDeal(times: Triple<String, String, String> = scheduled, totalQuantity: Int = 10): Long {
        val result = mockMvc.post("/api/v1/admin/deals") {
            header("Authorization", "Bearer ${adminToken()}")
            contentType = MediaType.APPLICATION_JSON
            content = dealBody(times, totalQuantity)
        }.andExpect { status { isCreated() } }.andReturn()
        return json(result).get("data").get("dealId").asLong()
    }

    @Test
    fun 관리자가_딜을_등록하면_상세에서_남은수량이_총수량이다() {
        val dealId = createDeal(totalQuantity = 10)

        mockMvc.get("/api/v1/deals/$dealId").andExpect {
            status { isOk() }
            jsonPath("$.message") { isNotEmpty() }
            jsonPath("$.data.dealId") { value(dealId) }
            jsonPath("$.data.productName") { value("예정딜") }
            jsonPath("$.data.originalPrice") { value(10000) }
            jsonPath("$.data.dealPrice") { value(5000) }
            jsonPath("$.data.totalQuantity") { value(10) }
            jsonPath("$.data.remainingQuantity") { value(10) }
            jsonPath("$.data.perMemberLimit") { value(2) }
            jsonPath("$.data.status") { value("SCHEDULED") }
        }
    }

    @Test
    fun 일시는_오프셋_없는_LocalDateTime으로_입출력된다() {
        val noOffset = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$""")
        val dealId = createDeal(scheduled)

        val detail = json(mockMvc.get("/api/v1/deals/$dealId").andExpect { status { isOk() } }.andReturn())
        assertEquals("2026-12-09T10:00:00", detail.get("data").get("openAt").asString())
        assertEquals("2026-12-10T10:00:00", detail.get("data").get("closeAt").asString())
        assertTrue(noOffset.matches(detail.get("timestamp").asString()), detail.get("timestamp").asString())

        val list = json(mockMvc.get("/api/v1/deals").andReturn())
        assertEquals("2026-12-09T10:00:00", list.get("data").get("content").get(0).get("openAt").asString())

        val error = json(mockMvc.get("/api/v1/deals/999999").andReturn())
        assertTrue(noOffset.matches(error.get("timestamp").asString()), error.get("timestamp").asString())
    }

    @Test
    fun 상태별_목록_조회() {
        createDeal(scheduled)
        createDeal(ongoing)
        createDeal(closed)

        fun names(status: String?): List<String> {
            val result = mockMvc.get("/api/v1/deals") { status?.let { param("status", it) } }
                .andExpect { status { isOk() } }.andReturn()
            val content = json(result).get("data").get("content")
            return (0 until content.size()).map { content.get(it).get("productName").asString() }.sorted()
        }

        assertEquals(listOf("예정딜"), names("SCHEDULED"))
        assertEquals(listOf("진행딜"), names("ONGOING"))
        assertEquals(listOf("종료딜"), names("CLOSED"))
        assertEquals(listOf("예정딜", "종료딜", "진행딜"), names(null))
    }

    @Test
    fun 목록은_페이징된다() {
        repeat(3) { createDeal(scheduled) }

        mockMvc.get("/api/v1/deals") {
            param("page", "0")
            param("size", "2")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(2) }
            jsonPath("$.data.page") { value(0) }
            jsonPath("$.data.size") { value(2) }
            jsonPath("$.data.totalElements") { value(3) }
            jsonPath("$.data.totalPages") { value(2) }
            jsonPath("$.data.hasNext") { value(true) }
            jsonPath("$.data.content[0].status") { value("SCHEDULED") }
        }
        mockMvc.get("/api/v1/deals") { param("size", "101") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
        mockMvc.get("/api/v1/deals") { param("page", "-1") }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun 삭제된_딜은_조회되지_않는다() {
        val dealId = createDeal(scheduled)
        val token = adminToken()

        mockMvc.delete("/api/v1/admin/deals/$dealId") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNoContent() } }

        val list = mockMvc.get("/api/v1/deals").andReturn()
        assertEquals(0, json(list).get("data").get("content").size())
        mockMvc.get("/api/v1/deals/$dealId").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("DEAL_NOT_FOUND") }
        }
        assertEquals("DELETED", jdbcTemplate.queryForObject("select status from deal where id = ?", String::class.java, dealId))
        assertEquals("DELETED", jdbcTemplate.queryForObject("select status from stock where deal_id = ?", String::class.java, dealId))
        mockMvc.delete("/api/v1/admin/deals/$dealId") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun 오픈_전_수정하면_재고도_새_총수량으로_초기화된다() {
        val dealId = createDeal(scheduled, totalQuantity = 10)

        mockMvc.put("/api/v1/admin/deals/$dealId") {
            header("Authorization", "Bearer ${adminToken()}")
            contentType = MediaType.APPLICATION_JSON
            content = dealBody(scheduled, totalQuantity = 30, dealPrice = 4000)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data") { doesNotExist() }
        }

        mockMvc.get("/api/v1/deals/$dealId").andExpect {
            jsonPath("$.data.totalQuantity") { value(30) }
            jsonPath("$.data.remainingQuantity") { value(30) }
            jsonPath("$.data.dealPrice") { value(4000) }
        }
    }

    @Test
    fun 오픈_후_수정은_409() {
        val dealId = createDeal(ongoing)
        val token = adminToken()

        mockMvc.put("/api/v1/admin/deals/$dealId") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = dealBody(ongoing, totalQuantity = 30)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DEAL_ALREADY_OPENED") }
        }
        mockMvc.delete("/api/v1/admin/deals/$dealId") { header("Authorization", "Bearer $token") }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DEAL_ALREADY_OPENED") }
        }
    }

    @Test
    fun 없는_딜_수정은_404() {
        mockMvc.put("/api/v1/admin/deals/999999") {
            header("Authorization", "Bearer ${adminToken()}")
            contentType = MediaType.APPLICATION_JSON
            content = dealBody()
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("DEAL_NOT_FOUND") }
        }
    }

    @Test
    fun 잘못된_딜_요청은_400() {
        val token = adminToken()
        mockMvc.post("/api/v1/admin/deals") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = dealBody(originalPrice = 1000, dealPrice = 5000)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("DEAL_INVALID") }
        }
        mockMvc.post("/api/v1/admin/deals") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = dealBody(totalQuantity = 0)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
        mockMvc.post("/api/v1/admin/deals") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun 일반_회원은_딜을_등록할_수_없다() {
        val email = "user-${UUID.randomUUID()}@kairos.com"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password1", "name" to "홍길동"))
        }
        val login = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "password1"))
        }.andReturn()
        val userToken = json(login).get("data").get("accessToken").asString()

        mockMvc.post("/api/v1/admin/deals") {
            header("Authorization", "Bearer $userToken")
            contentType = MediaType.APPLICATION_JSON
            content = dealBody()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }
}
