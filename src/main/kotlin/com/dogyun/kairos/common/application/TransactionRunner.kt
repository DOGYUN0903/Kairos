package com.dogyun.kairos.common.application

/** 결제 승인·취소처럼 PG 호출을 사이에 둔 여러 트랜잭션을 application 에서 직접 나눠 열기 위한 포트. */
interface TransactionRunner {
    fun <T> run(block: () -> T): T
}
