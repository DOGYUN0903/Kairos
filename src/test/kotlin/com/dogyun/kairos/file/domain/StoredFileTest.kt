package com.dogyun.kairos.file.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoredFileTest {

    private fun file(size: Long, contentType: String = "image/png") =
        StoredFile(null, "images/a.png", "a.png", contentType, size, 1L)

    @Test
    fun gif는_FILE_UNSUPPORTED_FORMAT() {
        val e = assertFailsWith<DomainException> { ImageFormat.fromContentType("image/gif") }
        assertEquals(ErrorCode.FILE_UNSUPPORTED_FORMAT, e.errorCode)
        assertEquals(ErrorCode.FILE_UNSUPPORTED_FORMAT, assertFailsWith<DomainException> { ImageFormat.fromContentType(null) }.errorCode)
    }

    @Test
    fun 허용_형식은_확장자로_매핑된다() {
        assertEquals("jpg", ImageFormat.fromContentType("image/jpeg").extension)
        assertEquals("webp", ImageFormat.fromContentType("image/webp").extension)
    }

    @Test
    fun `0바이트는_FILE_INVALID`() {
        assertEquals(ErrorCode.FILE_INVALID, assertFailsWith<DomainException> { file(0) }.errorCode)
    }

    @Test
    fun `5MB_초과는_FILE_INVALID`() {
        assertEquals(ErrorCode.FILE_INVALID, assertFailsWith<DomainException> { file(5_242_881) }.errorCode)
        assertTrue(file(5_242_880).isImage)
    }
}
