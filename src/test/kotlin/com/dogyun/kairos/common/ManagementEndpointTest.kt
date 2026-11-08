package com.dogyun.kairos.common

import com.dogyun.kairos.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import kotlin.test.assertEquals

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["management.server.port=0"],
)
@AutoConfigureMetrics
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ManagementEndpointTest {

    @LocalServerPort
    private var serverPort: Int = 0

    @LocalManagementPort
    private var managementPort: Int = 0

    private val client = HttpClient.newHttpClient()

    private fun get(port: Int, path: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun 관리_포트에서_prometheus_지표를_노출한다() {
        val response = get(managementPort, "/actuator/prometheus")

        assertEquals(200, response.statusCode())
        assertContains(response.body(), "jvm_")
        assertContains(response.body(), "hikaricp_connections")
        assertContains(response.body(), "tomcat_threads")
    }

    @Test
    fun 서비스_포트에는_actuator가_없다() {
        val response = get(serverPort, "/actuator/prometheus")

        assertNotEquals(200, response.statusCode())
    }
}
