package com.dogyun.kairos.file.application

import com.dogyun.kairos.file.domain.ImageFormat
import com.dogyun.kairos.file.domain.StoredFile
import com.dogyun.kairos.file.domain.StoredFileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FileService(
    private val repository: StoredFileRepository,
    private val imageStorage: ImageStorage,
    private val fileQueryService: FileQueryService,
) {

    @Transactional
    fun uploadImage(memberId: Long, originalName: String?, contentType: String?, bytes: ByteArray): UploadResult {
        val format = ImageFormat.fromContentType(contentType)
        val storageKey = "images/${UUID.randomUUID()}.${format.extension}"
        // 크기 검증은 StoredFile 생성에서 한다. 검증을 통과해야 파일을 쓴다.
        val saved = repository.save(
            StoredFile(
                id = null,
                storageKey = storageKey,
                originalName = originalName?.takeIf { it.isNotBlank() }?.take(255) ?: "image.${format.extension}",
                contentType = format.contentType,
                sizeBytes = bytes.size.toLong(),
                uploaderMemberId = memberId,
            ),
        )
        imageStorage.store(storageKey, bytes, format.contentType)
        return UploadResult(requireNotNull(saved.id), fileQueryService.urlOf(storageKey))
    }
}
