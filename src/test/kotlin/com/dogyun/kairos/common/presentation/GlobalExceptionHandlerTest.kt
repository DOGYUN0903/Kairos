package com.dogyun.kairos.common.presentation

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test

/** 컨텍스트 없이 던지는 컨트롤러만 붙인 standalone MockMvc로 프레임워크/예기치 못한 예외 매핑을 검증한다. */
class GlobalExceptionHandlerTest {

    @RestController
    class ThrowingController {
        @GetMapping("/boom")
        fun boom(): String = throw RuntimeException("boom")

        @PostMapping("/json", consumes = ["application/json"])
        fun json(): String = "ok"
    }

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(ThrowingController())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun 예기치_못한_예외는_500_INTERNAL_ERROR() {
        mockMvc.get("/boom").andExpect {
            status { isInternalServerError() }
            jsonPath("$.code") { value("INTERNAL_ERROR") }
            jsonPath("$.timestamp") { exists() }
        }
    }

    @Test
    fun 지원하지_않는_메서드는_405_METHOD_NOT_ALLOWED() {
        mockMvc.post("/boom").andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value("METHOD_NOT_ALLOWED") }
        }
    }

    @Test
    fun 지원하지_않는_Content_Type은_415_상태를_유지하고_ErrorResponse_본문() {
        mockMvc.post("/json") {
            contentType = org.springframework.http.MediaType.TEXT_PLAIN
            content = "x"
        }.andExpect {
            status { isUnsupportedMediaType() }
            jsonPath("$.code") { value("INVALID_REQUEST") }
            jsonPath("$.timestamp") { exists() }
        }
    }
}
