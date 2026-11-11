package com.dogyun.kairos.demo.infrastructure

import com.dogyun.kairos.demo.application.DemoSeeder
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
    @Suppress("unused") private val guard: DemoProfileGuard,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val result = demoSeeder.seed()
        log.info { "데모 데이터 시드 완료(새로 만든 수): $result" }
    }
}
