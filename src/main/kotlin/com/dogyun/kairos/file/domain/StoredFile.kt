package com.dogyun.kairos.file.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

class StoredFile(
    val id: Long?,
    val storageKey: String,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploaderMemberId: Long,
) {
    init {
        if (sizeBytes !in 1..MAX_SIZE_BYTES) throw DomainException(ErrorCode.FILE_INVALID)
    }

    val isImage: Boolean get() = ImageFormat.entries.any { it.contentType == contentType }

    companion object {
        const val MAX_SIZE_BYTES = 5_242_880L
    }
}
