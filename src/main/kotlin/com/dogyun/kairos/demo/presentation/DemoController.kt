package com.dogyun.kairos.demo.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.demo.application.DemoReseedResult
import com.dogyun.kairos.demo.application.DemoSeeder
import com.dogyun.kairos.demo.application.DemoSettlementSeeder
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** demo 프로필에서만 존재한다. 권한(ADMIN)은 SecurityConfig 가 건다. */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo")
class DemoController(
    private val demoSeeder: DemoSeeder,
    private val settlementSeeder: DemoSettlementSeeder,
) {

    /** 부족한 데모 데이터만 채운다(멱등, 삭제 없음). 지난주 정산 시연 묶음이 없으면 그것도 만든다(응답 숫자에는 넣지 않는다). */
    @PostMapping("/reseed")
    fun reseed(): ResponseEntity<ApiResponse<DemoReseedResult>> {
        val result = demoSeeder.seed()
        settlementSeeder.seedLastWeek()
        return ApiResponse.ok(result)
    }
}
