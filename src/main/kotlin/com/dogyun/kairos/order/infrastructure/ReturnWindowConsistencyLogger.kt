package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.OrderProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** 기동 시 반품 가능 기간과 자동 구매 확정 기간이 다르면 경고한다. */
@Component
class ReturnWindowConsistencyLogger(private val properties: OrderProperties) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (properties.returnWindowMismatch()) {
            log.warn(
                "kairos.order.return-window({})와 auto-confirm-after({})가 다르다. 열린 반품이 있는 채로 자동 구매 확정 시각이 먼저 올 수 있다.",
                properties.returnWindow, properties.autoConfirmAfter,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ReturnWindowConsistencyLogger::class.java)
    }
}
