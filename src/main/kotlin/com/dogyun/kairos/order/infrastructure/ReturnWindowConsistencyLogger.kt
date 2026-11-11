package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.order.application.OrderProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/** 기동 시 반품 가능 기간과 자동 구매 확정 기간이 다르면 경고한다. */
@Component
class ReturnWindowConsistencyLogger(private val properties: OrderProperties) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (properties.returnWindowMismatch()) {
            log.warn {
                "kairos.order.return-window(${properties.returnWindow})와 auto-confirm-after(${properties.autoConfirmAfter})가 다르다. 열린 반품이 있는 채로 자동 구매 확정 시각이 먼저 올 수 있다."
            }
        }
    }
}
