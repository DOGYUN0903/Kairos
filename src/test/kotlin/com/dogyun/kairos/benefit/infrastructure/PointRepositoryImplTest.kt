package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.benefit.domain.PointLot
import com.dogyun.kairos.benefit.domain.PointRepository
import com.dogyun.kairos.benefit.domain.PointSourceType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointRepositoryImplTest : CommerceTestSupport() {

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    private val now = LocalDateTime.now().withNano(0)

    private fun rows(memberId: Long): Int =
        jdbcTemplate.queryForObject("select count(*) from member_point where member_id = ?", Int::class.java, memberId) ?: 0

    @Test
    fun lockOrCreate는_없으면_만들고_있으면_잠근다() {
        val memberId = 9_001L
        val tx = TransactionTemplate(txManager)
        val pool = Executors.newFixedThreadPool(2)
        val firstLocked = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        try {
            val first = pool.submit {
                tx.executeWithoutResult {
                    val created = pointRepository.lockOrCreate(memberId)
                    assertEquals(0, created.balance)
                    firstLocked.countDown()
                    releaseFirst.await(10, TimeUnit.SECONDS)
                    created.add(500)
                    pointRepository.save(created)
                }
            }
            assertTrue(firstLocked.await(10, TimeUnit.SECONDS))
            val second = pool.submit<Long> {
                tx.execute { pointRepository.lockOrCreate(memberId).balance }
            }
            Thread.sleep(500)
            assertTrue(!second.isDone, "첫 트랜잭션이 커밋하기 전에는 두 번째가 기다려야 한다")
            releaseFirst.countDown()
            first.get(10, TimeUnit.SECONDS)
            assertEquals(500L, second.get(10, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            pool.shutdownNow()
        }
        assertEquals(1, rows(memberId))
    }

    @Test
    fun 동시_lockOrCreate_10건은_member_point_한_행이다() {
        val memberId = 9_002L
        val tx = TransactionTemplate(txManager)
        val pool = Executors.newFixedThreadPool(10)
        val start = CountDownLatch(1)
        try {
            val futures = (1..10).map {
                pool.submit {
                    start.await()
                    tx.executeWithoutResult {
                        val p = pointRepository.lockOrCreate(memberId)
                        p.add(1)
                        pointRepository.save(p)
                    }
                }
            }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, rows(memberId))
        assertEquals(10L, pointBalance(memberId))
    }

    @Test
    fun findExpiredLots는_id_keyset으로_만료_로트만_준다() {
        fun lot(memberId: Long, remaining: Long, expiresAt: LocalDateTime) = requireNotNull(
            pointRepository.saveLot(PointLot(null, memberId, 100, remaining, PointSourceType.PURCHASE, 1, expiresAt)).id,
        )
        val expired1 = lot(1, 100, now.minusDays(1))
        lot(1, 100, now.plusDays(1)) // 만료 전
        lot(2, 0, now.minusDays(1)) // 남은 양 없음
        val expired2 = lot(2, 40, now.minusSeconds(1))
        val expired3 = lot(3, 10, now.minusDays(3))

        val firstPage = pointRepository.findExpiredLots(now, 0, 2)
        assertEquals(listOf(1L to expired1, 2L to expired2), firstPage)
        val secondPage = pointRepository.findExpiredLots(now, expired2, 2)
        assertEquals(listOf(3L to expired3), secondPage)
    }
}
