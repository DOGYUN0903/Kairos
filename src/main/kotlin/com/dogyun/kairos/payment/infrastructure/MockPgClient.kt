package com.dogyun.kairos.payment.infrastructure

import com.dogyun.kairos.payment.application.PgCancel
import com.dogyun.kairos.payment.application.PgClient
import com.dogyun.kairos.payment.application.PgPayment
import com.dogyun.kairos.payment.application.PgResult
import com.dogyun.kairos.payment.application.PgStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 로컬·테스트용 PG. 동작은 paymentKey 접두사로만 고른다(취소 사유 등 다른 값으로 고르지 않는다).
 * - `fail-`: 승인 거절 / `unknown-`: 승인은 됐지만 응답을 못 받음 / `lost-`: 응답도 못 받고 PG 에도 없음
 * - `cancelfail-`: 취소 거절 / `cancelunknown-`: 취소는 됐지만 응답을 못 받음 / `cancellost-`: 첫 취소 시도는 반영되지 않고 응답도 못 받음(같은 멱등 키로 다시 부르면 정상 처리)
 */
@Component
@ConditionalOnProperty("kairos.pg.provider", havingValue = "mock", matchIfMissing = true)
class MockPgClient : PgClient {

    private val payments = ConcurrentHashMap<String, PgPayment>()
    private val cancelIdempotencyKeys = ConcurrentHashMap.newKeySet<String>()
    private val lostCancelAttempts = ConcurrentHashMap.newKeySet<String>()

    override fun confirm(paymentKey: String, pgOrderId: String, amount: Long): PgResult {
        if (paymentKey.startsWith("fail-")) return PgResult.Failed("REJECT_CARD_COMPANY", "카드사가 승인을 거절했습니다.")
        if (paymentKey.startsWith("lost-")) return PgResult.Unknown("응답 시간 초과")
        val payment = PgPayment(paymentKey, pgOrderId, PgStatus.DONE, amount, amount, emptyList())
        payments.putIfAbsent(paymentKey, payment)
        if (paymentKey.startsWith("unknown-")) return PgResult.Unknown("응답 시간 초과")
        return PgResult.Done(payments.getValue(paymentKey))
    }

    @Synchronized
    override fun cancel(paymentKey: String, amount: Long, reason: String, idempotencyKey: String): PgResult {
        val current = payments[paymentKey] ?: return PgResult.Failed("NOT_FOUND", "존재하지 않는 결제입니다.")
        if (paymentKey.startsWith("cancelfail-")) return PgResult.Failed("REJECT_CANCEL", "취소가 거절되었습니다.")
        // 요청이 PG 에 닿지 못한 첫 시도만 유실된다. 대사가 같은 멱등 키로 다시 부르면 처리된다.
        if (paymentKey.startsWith("cancellost-") && lostCancelAttempts.add("$paymentKey:$idempotencyKey")) {
            return PgResult.Unknown("응답 시간 초과")
        }
        // 같은 멱등 키는 한 번만 기록한다.
        val updated = if (cancelIdempotencyKeys.add("$paymentKey:$idempotencyKey")) {
            if (amount <= 0 || amount > current.balanceAmount) {
                cancelIdempotencyKeys.remove("$paymentKey:$idempotencyKey")
                return PgResult.Failed("INVALID_CANCEL_AMOUNT", "취소 가능 금액을 초과했습니다.")
            }
            val balance = current.balanceAmount - amount
            current.copy(
                status = if (balance == 0L) PgStatus.CANCELED else PgStatus.PARTIAL_CANCELED,
                balanceAmount = balance,
                cancels = current.cancels + PgCancel(amount, reason),
            ).also { payments[paymentKey] = it }
        } else {
            current
        }
        if (paymentKey.startsWith("cancelunknown-")) return PgResult.Unknown("응답 시간 초과")
        return PgResult.Done(updated)
    }

    override fun find(paymentKey: String): PgResult =
        payments[paymentKey]?.let { PgResult.Done(it) } ?: PgResult.Failed("NOT_FOUND", "존재하지 않는 결제입니다.")
}
