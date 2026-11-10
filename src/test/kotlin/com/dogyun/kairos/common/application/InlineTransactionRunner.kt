package com.dogyun.kairos.common.application

/** 테스트용: 트랜잭션 없이 블록을 그대로 실행한다. */
class InlineTransactionRunner : TransactionRunner {
    override fun <T> run(block: () -> T): T = block()
}
