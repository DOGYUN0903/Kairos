package com.dogyun.kairos.payment.application

enum class PgResponse { APPROVED, FAILED, TIMEOUT }

interface PgClient {
    /** 호출 스레드를 블로킹하며 PG 응답을 기다린다. */
    fun approve(orderId: Long, amount: Long): PgResponse
}
