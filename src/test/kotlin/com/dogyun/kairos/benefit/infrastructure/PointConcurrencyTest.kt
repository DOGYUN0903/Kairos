package com.dogyun.kairos.benefit.infrastructure

import com.dogyun.kairos.CommerceTestSupport
import com.dogyun.kairos.benefit.domain.PointRefType
import com.dogyun.kairos.benefit.domain.PointSourceType
import com.dogyun.kairos.benefit.domain.PointTransactionType
import com.dogyun.kairos.common.domain.DomainException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** member_point 락 뒤의 읽기가 모두 현재 읽기인지(낡은 REPEATABLE READ 스냅샷을 쓰지 않는지) 검증한다. */
class PointConcurrencyTest : CommerceTestSupport() {

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    private val memberIdBase = 7_000L

    private fun <T> concurrently(n: Int, task: (Int) -> T): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(n)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until n).map { i ->
                pool.submit(Callable { start.await(); runCatching { task(i) } })
            }
            start.countDown()
            return futures.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun ledgerCount(type: String): Int =
        jdbcTemplate.queryForObject("select count(*) from point_transaction where transaction_type = ?", Int::class.java, type) ?: 0

    @Test
    fun 같은_회원의_동시_use는_초과_소비하지_않고_로트_합이_잔액과_같다() {
        val memberId = memberIdBase + 1
        grantPoints(memberId, 600)
        grantPoints(memberId, 400)

        val results = concurrently(12) { i -> pointService.use(memberId, 100 + i.toLong(), 100) }

        results.filter { it.isFailure }.forEach { assertTrue(it.exceptionOrNull() is DomainException, "${it.exceptionOrNull()}") }
        assertEquals(10, results.count { it.isSuccess })
        assertEquals(0L, pointBalance(memberId))
        assertEquals(pointBalance(memberId), lotRemainingSum(memberId))
        assertEquals(10, ledgerCount("USE"))
    }

    @Test
    fun 호출_트랜잭션이_먼저_일반_읽기를_했어도_use는_현재_로트를_본다() {
        val memberId = memberIdBase + 2
        grantPoints(memberId, 500)
        val tx = TransactionTemplate(txManager)
        val pool = Executors.newFixedThreadPool(2)
        val snapshotTaken = CountDownLatch(1)
        val otherCommitted = CountDownLatch(1)
        try {
            val caller = pool.submit {
                tx.executeWithoutResult {
                    // 이 일반 읽기가 REPEATABLE READ 스냅샷을 고정한다(주문 트랜잭션이 benefit 호출 전에 읽는 상황).
                    assertEquals(500L, jdbcTemplate.queryForObject("select sum(remaining_amount) from point_lot where member_id = ?", Long::class.java, memberId))
                    assertEquals(1, jdbcTemplate.queryForObject("select count(*) from point_transaction where member_id = ?", Int::class.java, memberId))
                    snapshotTaken.countDown()
                    assertTrue(otherCommitted.await(20, TimeUnit.SECONDS))
                    pointService.use(memberId, 2, 100)
                }
            }
            val other = pool.submit {
                assertTrue(snapshotTaken.await(20, TimeUnit.SECONDS))
                pointService.use(memberId, 1, 400)
                otherCommitted.countDown()
            }
            other.get(30, TimeUnit.SECONDS)
            caller.get(30, TimeUnit.SECONDS)
        } finally {
            otherCommitted.countDown()
            pool.shutdownNow()
        }
        assertEquals(0L, pointBalance(memberId))
        assertEquals(0L, lotRemainingSum(memberId))
    }

    @Test
    fun 호출_트랜잭션이_먼저_일반_읽기를_했어도_중복_적립은_예외없이_false다() {
        val memberId = memberIdBase + 3
        grantPoints(memberId, 10)
        val tx = TransactionTemplate(txManager)
        val pool = Executors.newFixedThreadPool(2)
        val snapshotTaken = CountDownLatch(1)
        val otherCommitted = CountDownLatch(1)
        val ref = 555L
        try {
            val caller = pool.submit<Boolean> {
                tx.execute {
                    jdbcTemplate.queryForObject("select count(*) from point_transaction where member_id = ?", Int::class.java, memberId)
                    snapshotTaken.countDown()
                    assertTrue(otherCommitted.await(20, TimeUnit.SECONDS))
                    earn(memberId, ref)
                }
            }
            val other = pool.submit<Boolean> {
                assertTrue(snapshotTaken.await(20, TimeUnit.SECONDS))
                earn(memberId, ref).also { otherCommitted.countDown() }
            }
            assertTrue(other.get(30, TimeUnit.SECONDS))
            assertEquals(false, caller.get(30, TimeUnit.SECONDS))
        } finally {
            otherCommitted.countDown()
            pool.shutdownNow()
        }
        assertEquals(60L, pointBalance(memberId))
    }

    private fun earn(memberId: Long, ref: Long): Boolean = pointService.earn(
        memberId, 50, PointTransactionType.EARN_REVIEW, PointSourceType.REVIEW, PointRefType.REVIEW, ref, LocalDateTime.now().plusDays(30),
    )

    @Test
    fun 같은_ref로_동시에_적립하면_한_번만_true고_예외가_없다() {
        // 기존 회원과 신규 회원 모두
        val existing = memberIdBase + 4
        grantPoints(existing, 10)
        val fresh = memberIdBase + 5
        listOf(existing, fresh).forEach { memberId ->
            val results = concurrently(8) { earn(memberId, 900 + memberId) }
            assertTrue(results.all { it.isSuccess }, results.mapNotNull { it.exceptionOrNull() }.joinToString())
            assertEquals(1, results.count { it.getOrThrow() })
        }
        assertEquals(60L, pointBalance(existing))
        assertEquals(50L, pointBalance(fresh))
        assertEquals(2, ledgerCount("EARN_REVIEW"))
    }

    @Test
    fun 서로_다른_회원의_동시_적립은_교착하지_않는다() {
        val results = concurrently(10) { i -> earn(memberIdBase + 100 + i, 3_000L + i) }
        assertTrue(results.all { it.isSuccess }, results.mapNotNull { it.exceptionOrNull() }.joinToString())
        assertEquals(10, ledgerCount("EARN_REVIEW"))
    }

    @Test
    fun 같은_주문의_동시_부분_되돌림은_사용액을_넘겨_되돌리지_않는다() {
        val memberId = memberIdBase + 6
        grantPoints(memberId, 1_000)
        pointService.use(memberId, 77, 600)

        val results = concurrently(2) { i -> pointService.refund(memberId, 77, PointRefType.ORDER_ITEM, 10L + i, 400) }

        assertEquals(1, results.count { it.isSuccess && it.getOrThrow() })
        assertEquals(1, results.count { it.exceptionOrNull() is IllegalStateException })
        assertEquals(800L, pointBalance(memberId))
        assertEquals(pointBalance(memberId), lotRemainingSum(memberId))
    }

    @Test
    fun 같은_항목의_동시_되돌림은_한_번만_반영된다() {
        val memberId = memberIdBase + 7
        grantPoints(memberId, 1_000)
        pointService.use(memberId, 78, 600)

        val results = concurrently(6) { pointService.refund(memberId, 78, PointRefType.ORDER_ITEM, 20, 200) }

        assertTrue(results.all { it.isSuccess })
        assertEquals(1, results.count { it.getOrThrow() })
        assertEquals(600L, pointBalance(memberId))
        assertEquals(pointBalance(memberId), lotRemainingSum(memberId))
    }

    @Test
    fun 만료와_사용이_겹쳐도_로트_합이_잔액과_같다() {
        val memberId = memberIdBase + 8
        grantPoints(memberId, 500, LocalDateTime.now().plusSeconds(1))
        grantPoints(memberId, 500)
        Thread.sleep(1_500)
        val expiredLot = expiredLotId(memberId)

        val results = concurrently(4) { i ->
            if (i % 2 == 0) pointService.use(memberId, 90L + i, 100).let { true } else pointService.expireLot(memberId, expiredLot)
        }

        results.forEach { r -> r.exceptionOrNull()?.let { assertTrue(it is DomainException, "$it") } }
        assertEquals(pointBalance(memberId), lotRemainingSum(memberId))
    }

    private fun expiredLotId(memberId: Long): Long =
        jdbcTemplate.queryForObject("select id from point_lot where member_id = ? and expires_at < ? order by id limit 1", Long::class.java, memberId, LocalDateTime.now())!!
}
