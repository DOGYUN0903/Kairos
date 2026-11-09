package com.dogyun.kairos.file.presentation

import com.dogyun.kairos.CommerceTestSupport
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import kotlin.test.Test
import kotlin.test.assertContentEquals

class FileApiTest : CommerceTestSupport() {

    private fun upload(token: String?, file: MockMultipartFile): ResultActionsDsl =
        mockMvc.multipart("/api/v1/files/images") {
            file(file)
            token?.let { header("Authorization", "Bearer $it") }
        }

    @Test
    fun PNG를_올리면_201과_URL을_받고_비로그인_GET으로_내려받을_수_있다() {
        val token = newUserToken()
        val file = MockMultipartFile("file", "dot.png", "image/png", PNG_1X1)

        val data = json(upload(token, file).andExpect { status { isCreated() } }).get("data")
        val url = data.get("url").asString()
        val path = url.substringAfter("http://localhost:8080")

        val body = mockMvc.get(path).andExpect {
            status { isOk() }
            content { contentType("image/png") }
        }.andReturn().response.contentAsByteArray
        assertContentEquals(PNG_1X1, body)
    }

    @Test
    fun 텍스트_파일은_400_FILE_UNSUPPORTED_FORMAT() {
        upload(newUserToken(), MockMultipartFile("file", "a.txt", "text/plain", "hi".toByteArray())).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("FILE_UNSUPPORTED_FORMAT") }
        }
    }

    @Test
    fun 비로그인_업로드는_401() {
        upload(null, MockMultipartFile("file", "dot.png", "image/png", PNG_1X1)).andExpect {
            status { isUnauthorized() }
        }
    }
}
