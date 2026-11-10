package com.dogyun.kairos.benefit.presentation

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.benefit.application.PointExpiryService
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PointApiTest : CommerceTestSupport() {

    @Autowired
    private lateinit var expiryService: PointExpiryService

    @Test
    fun 적립한_포인트가_잔액과_내역에_보이고_30일_내_만료_예정이_맞다() {
        val token = newUserToken()
        val memberId = memberIdOf(token)
        grantPoints(memberId, 1_000, LocalDateTime.now().plusDays(10))
        grantPoints(memberId, 700, LocalDateTime.now().plusDays(100))

        val summary = json(get("/api/v1/points/me", token).andExpect { status { isOk() } }).get("data")
        assertEquals(1_700L, summary.get("balance").asLong())
        assertEquals(1_000L, summary.get("expiringWithin30Days").asLong())

        val page = json(get("/api/v1/points/me/transactions?page=0&size=10", token).andExpect { status { isOk() } }).get("data")
        assertEquals(2, page.get("content").size())
        val latest = page.get("content").get(0)
        assertEquals("EARN_PURCHASE", latest.get("type").asString())
        assertEquals(700L, latest.get("amount").asLong())
        assertEquals(1_700L, latest.get("balanceAfter").asLong())
        assertEquals("PURCHASE_CONFIRM", latest.get("refType").asString())
    }

    @Test
    fun 만료_작업을_직접_호출하면_만료_로트가_0이_되고_잔액이_준다() {
        val token = newUserToken()
        val memberId = memberIdOf(token)
        grantPoints(memberId, 400, LocalDateTime.now().minusMinutes(1))
        grantPoints(memberId, 600, LocalDateTime.now().plusDays(30))

        assertEquals(1, expiryService.expireLots())
        assertEquals(0, expiryService.expireLots())

        assertEquals(600L, pointBalance(memberId))
        assertEquals(600L, lotRemainingSum(memberId))
        val summary = json(get("/api/v1/points/me", token).andExpect { status { isOk() } }).get("data")
        assertEquals(600L, summary.get("balance").asLong())
    }

    @Test
    fun 관리자는_회원_포인트를_본다_일반_회원은_403() {
        val token = newUserToken()
        val memberId = memberIdOf(token)
        grantPoints(memberId, 300)

        val data = json(get("/api/v1/admin/members/$memberId/points", adminToken()).andExpect { status { isOk() } }).get("data")
        assertEquals(300L, data.get("balance").asLong())
        assertEquals(1, data.get("transactions").get("content").size())

        get("/api/v1/admin/members/$memberId/points", token).andExpect { status { isForbidden() } }
        get("/api/v1/points/me").andExpect { status { isUnauthorized() } }
    }
}
