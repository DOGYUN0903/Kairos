package com.dogyun.kairos.common.idempotency

import java.time.Duration
import java.time.LocalDateTime

sealed interface BeginResult {
    /** 이 요청이 처리 권한을 얻었다. */
    data object Started : BeginResult

    /** 같은 키의 요청이 처리 중이다. */
    data object InProgress : BeginResult

    /** 같은 키·같은 본문의 요청이 이미 끝났다. 저장된 응답을 다시 돌려준다. */
    data class Completed(val status: Int, val body: String) : BeginResult

    /** 같은 키로 다른 요청이 왔다. */
    data object HashMismatch : BeginResult
}

interface IdempotencyStore {
    fun begin(
        memberId: Long,
        key: String,
        requestHash: String,
        now: LocalDateTime,
        expiresAt: LocalDateTime,
        inProgressTimeout: Duration,
    ): BeginResult

    fun complete(memberId: Long, key: String, status: Int, body: String)

    /** 완료되지 않은(처리 중) 키를 지워 재시도할 수 있게 한다. */
    fun release(memberId: Long, key: String)

    fun deleteExpired(now: LocalDateTime): Int
}
