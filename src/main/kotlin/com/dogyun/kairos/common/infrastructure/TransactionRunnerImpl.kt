package com.dogyun.kairos.common.infrastructure

import com.dogyun.kairos.common.application.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class TransactionRunnerImpl(private val transactionTemplate: TransactionTemplate) : TransactionRunner {

    @Suppress("UNCHECKED_CAST")
    override fun <T> run(block: () -> T): T = transactionTemplate.execute { block() } as T
}
