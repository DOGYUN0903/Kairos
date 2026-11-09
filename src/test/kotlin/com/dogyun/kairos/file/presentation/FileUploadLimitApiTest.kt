package com.dogyun.kairos.file.presentation

import com.dogyun.kairos.TestcontainersConfiguration
import com.dogyun.kairos.common.security.Role
import com.dogyun.kairos.common.security.JwtProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MockMvc 는 서블릿 컨테이너의 multipart 크기 제한을 적용하지 않으므로, 413 은 실제 서버로 확인한다.
 * (FileApiTest 의 케이스 `6MB_파일은_413` 과 같은 계약이다.)
 */
// Tomcat 은 413 응답 뒤 남은 본문을 더 읽지 않고 연결을 끊어 클라이언트가 응답 대신 reset 을 받을 수 있다.
// 응답 계약만 확인하려고 이 테스트에서만 swallow 제한을 푼다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.tomcat.max-swallow-size=-1"],
)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class FileUploadLimitApiTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Test
    fun `6MB_파일은_413`() {
        val boundary = "kairos-boundary"
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.png\"\r\nContent-Type: image/png\r\n\r\n"
        val tail = "\r\n--$boundary--\r\n"
        val body = head.toByteArray() + ByteArray(6 * 1024 * 1024) + tail.toByteArray()
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/files/images"))
            .header("Authorization", "Bearer ${jwtProvider.createAccessToken(1L, Role.USER)}")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(413, response.statusCode())
        assertTrue(response.body().contains("INVALID_REQUEST"))
    }
}
