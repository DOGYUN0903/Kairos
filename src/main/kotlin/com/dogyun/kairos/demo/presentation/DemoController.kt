package com.dogyun.kairos.demo.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.demo.application.DemoReseedResult
import com.dogyun.kairos.demo.application.DemoSeeder
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** demo 프로필에서만 존재한다. 권한(ADMIN)은 SecurityConfig 가 건다. */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo")
class DemoController(private val demoSeeder: DemoSeeder) {

    /** 부족한 데모 데이터만 채운다(멱등, 삭제 없음). */
    @PostMapping("/reseed")
    fun reseed(): ResponseEntity<ApiResponse<DemoReseedResult>> = ApiResponse.ok(demoSeeder.seed())
}
