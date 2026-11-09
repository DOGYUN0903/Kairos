package com.dogyun.kairos.file.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

enum class ImageFormat(val contentType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    companion object {
        fun fromContentType(contentType: String?): ImageFormat =
            entries.firstOrNull { it.contentType == contentType?.lowercase() }
                ?: throw DomainException(ErrorCode.FILE_UNSUPPORTED_FORMAT)
    }
}
