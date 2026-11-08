package com.dogyun.kairos.common

import com.dogyun.kairos.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.server.port=0"],
)
@AutoConfigureTracing
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class TraceIdTest {

    @LocalServerPort
    private var serverPort: Int = 0

    private val client = HttpClient.newHttpClient()

    private fun get(path: String, vararg headers: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$serverPort$path"))
                .apply { if (headers.isNotEmpty()) headers(*headers) }
                .GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun 응답헤더에_traceId를_돌려준다() {
        val response = get("/api/v1/deals/1")

        val traceId = response.headers().firstValue("X-Trace-Id").orElse(null)
        assertNotNull(traceId)
        assertTrue(Regex("[0-9a-f]{32}").matches(traceId), "traceId=$traceId")
    }

    @Test
    fun traceparent_헤더가_있으면_같은_traceId를_이어받는다() {
        val response = get(
            "/api/v1/deals/1",
            "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        )

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", response.headers().firstValue("X-Trace-Id").orElse(null))
    }

    @Test
    fun 인증_실패_응답에도_traceId가_있다() {
        val response = get("/api/v1/orders/me")

        assertEquals(401, response.statusCode())
        assertNotNull(response.headers().firstValue("X-Trace-Id").orElse(null))
    }
}
