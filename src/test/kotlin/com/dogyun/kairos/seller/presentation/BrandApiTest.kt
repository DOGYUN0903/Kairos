package com.dogyun.kairos.seller.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.test.web.servlet.ResultActionsDsl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrandApiTest : CommerceTestSupport() {

    private fun ResultActionsDsl.expectCode(httpStatus: Int, code: String): ResultActionsDsl =
        andExpect { status { isEqualTo(httpStatus) } }.andExpect { jsonPath("$.code") { value(code) } }

    private fun applyBrand(token: String, name: String = "키로스"): Long =
        json(post("/api/v1/seller/brands", token, mapOf("name" to name)).andExpect { status { isCreated() } })
            .get("data").get("brandId").asLong()

    @Test
    fun 로고와_함께_신청_승인_후_공개_페이지에_logoUrl이_보인다() {
        val token = newUserToken()
        approvedSeller(token)
        val fileId = uploadImage(token)

        val brandId = approvedBrand(token, "키로스", fileId)

        val data = json(get("/api/v1/brands/$brandId").andExpect { status { isOk() } }).get("data")
        assertEquals("키로스", data.get("name").asString())
        assertNotNull(data.get("logoUrl").asString())
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "select count(*) from brand_image where brand_id = ? and file_id = ? and purpose = 'LOGO'",
                Int::class.java, brandId, fileId,
            ),
        )
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from seller_review_history", Int::class.java) )
    }

    @Test
    fun 승인_전_공개_페이지는_404() {
        val token = newUserToken()
        approvedSeller(token)
        val brandId = applyBrand(token)

        get("/api/v1/brands/$brandId").expectCode(404, "BRAND_NOT_FOUND")
    }

    @Test
    fun 판매자_정지_후_공개_페이지는_404() {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val brandId = approvedBrand(token)

        suspendSeller(sellerId)
        get("/api/v1/brands/$brandId").expectCode(404, "BRAND_NOT_FOUND")
        // 정지된 판매자도 자기 브랜드 목록은 본다.
        get("/api/v1/seller/brands", token).andExpect { status { isOk() } }

        unsuspendSeller(sellerId)
        get("/api/v1/brands/$brandId").andExpect { status { isOk() } }
    }

    @Test
    fun PENDING_판매자의_브랜드_신청은_409_SELLER_NOT_ACTIVE() {
        val token = newUserToken()
        post(
            "/api/v1/seller/application", token,
            mapOf(
                "companyName" to "키로스상사", "businessNumber" to "123-45-67890", "representativeName" to "홍길동",
                "phone" to "010-1234-5678", "bank" to "국민", "accountNumber" to "123456-01-123456", "holder" to "홍길동",
            ),
        ).andExpect { status { isCreated() } }

        post("/api/v1/seller/brands", token, mapOf("name" to "키로스")).expectCode(409, "SELLER_NOT_ACTIVE")
    }

    @Test
    fun 판매자가_아닌_회원의_브랜드_신청은_403() {
        post("/api/v1/seller/brands", newUserToken(), mapOf("name" to "키로스")).expectCode(403, "FORBIDDEN")
    }

    @Test
    fun 다른_판매자의_브랜드_수정은_404() {
        val owner = newUserToken()
        approvedSeller(owner)
        val brandId = applyBrand(owner)
        post("/api/v1/admin/brands/$brandId/reject", adminToken(), mapOf("reason" to "불명확"))
            .andExpect { status { isOk() } }
        val other = newUserToken()
        approvedSeller(other)

        put("/api/v1/seller/brands/$brandId", other, mapOf("name" to "탈취"))
            .expectCode(404, "BRAND_NOT_FOUND")
    }

    @Test
    fun 반려_후_재신청하면_PENDING() {
        val token = newUserToken()
        approvedSeller(token)
        val brandId = applyBrand(token)
        val admin = adminToken()
        post("/api/v1/admin/brands/$brandId/reject", admin, mapOf("reason" to "로고 필요"))
            .andExpect { status { isOk() } }
        val rejected = json(get("/api/v1/seller/brands", token)).get("data").get(0)
        assertEquals("REJECTED", rejected.get("status").asString())
        assertEquals("로고 필요", rejected.get("rejectReason").asString())
        val fileId = uploadImage(token)

        put("/api/v1/seller/brands/$brandId", token, mapOf("name" to "키로스2", "logoFileId" to fileId))
            .andExpect { status { isOk() } }

        val mine = json(get("/api/v1/seller/brands", token)).get("data").get(0)
        assertEquals("PENDING", mine.get("status").asString())
        assertEquals("키로스2", mine.get("name").asString())
        assertNotNull(mine.get("logoUrl").asString())
        assertEquals(fileId, mine.get("logoFileId").asLong())
        val pending = json(get("/api/v1/admin/brands?status=PENDING", admin)).get("data")
        assertEquals(1, pending.get("totalElements").asInt())
        // 승인된 브랜드는 재신청할 수 없다.
        post("/api/v1/admin/brands/$brandId/approve", admin).andExpect { status { isOk() } }
        put("/api/v1/seller/brands/$brandId", token, mapOf("name" to "키로스3"))
            .expectCode(409, "BRAND_INVALID_STATE_TRANSITION")
    }

    @Test
    fun 브랜드명이_중복이면_409_BRAND_NAME_DUPLICATED() {
        val token = newUserToken()
        approvedSeller(token)
        applyBrand(token, "키로스")

        post("/api/v1/seller/brands", token, mapOf("name" to "키로스")).expectCode(409, "BRAND_NAME_DUPLICATED")
    }

    @Test
    fun 다른_회원의_파일을_로고로_쓰면_400_FILE_NOT_OWNED() {
        val token = newUserToken()
        approvedSeller(token)
        val others = uploadImage(newUserToken())

        post("/api/v1/seller/brands", token, mapOf("name" to "키로스", "logoFileId" to others))
            .expectCode(400, "FILE_NOT_OWNED")
    }

    private fun brandList(): List<Pair<Long, String>> {
        val data = json(get("/api/v1/brands").andExpect { status { isOk() } }).get("data")
        return (0 until data.size()).map { data.get(it).get("brandId").asLong() to data.get(it).get("name").asString() }
    }

    @Test
    fun 공개_브랜드_목록은_비로그인으로_승인_브랜드만_이름순으로_준다() {
        val token = newUserToken()
        approvedSeller(token)
        val b = approvedBrand(token, "가나다-목록테스트")
        val a = approvedBrand(token, "AAA-목록테스트")
        val pendingId = applyBrand(token, "대기-목록테스트")

        val list = brandList()

        val ids = list.map { it.first }
        assertTrue(a in ids && b in ids)
        assertTrue(pendingId !in ids)
        assertTrue(ids.indexOf(a) < ids.indexOf(b), "이름 오름차순: AAA < 가나다")
        assertEquals(list.map { it.second }, list.map { it.second }.sorted())
    }

    @Test
    fun 정지된_판매자의_브랜드는_공개_목록에서_빠지고_해제하면_돌아온다() {
        val token = newUserToken()
        val sellerId = approvedSeller(token)
        val brandId = approvedBrand(token)

        suspendSeller(sellerId)
        assertTrue(brandId !in brandList().map { it.first })

        unsuspendSeller(sellerId)
        assertTrue(brandId in brandList().map { it.first })
    }
}
