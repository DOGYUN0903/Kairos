package com.dogyun.kairos.demo.infrastructure

import com.dogyun.kairos.demo.application.DemoSeeder
import com.dogyun.kairos.demo.application.DemoSettlementSeeder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

/** demo 프로필 기동 시 부족한 데모 데이터를 채운다. */
@Component
@Profile("demo")
class DemoDataInitializer(
    private val demoSeeder: DemoSeeder,
    private val settlementSeeder: DemoSettlementSeeder,
    @Suppress("unused") private val guard: DemoProfileGuard,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val result = demoSeeder.seed()
        log.info { "데모 데이터 시드 완료(새로 만든 수): $result" }
        // 정산 시연 묶음은 실패해도 기동을 막지 않는다(스케줄러 스레드와 겹치면 실패할 수 있다). 다음 기동·reseed 가 완성 판정으로 다시 만든다.
        log.info { "정산 시연 데이터 시드 완료: ${settlementSeeder.seedLastWeekQuietly()}" }
    }
}
