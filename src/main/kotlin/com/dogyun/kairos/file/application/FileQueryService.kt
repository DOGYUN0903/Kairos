package com.dogyun.kairos.file.application

import com.dogyun.kairos.file.domain.StoredFileRepository
import com.dogyun.kairos.file.application.FileProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class FileQueryService(
    private val repository: StoredFileRepository,
    private val properties: FileProperties,
) {

    /** 존재하지 않는 ID는 결과에서 빠진다. */
    fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileInfo> {
        if (fileIds.isEmpty()) return emptyMap()
        return repository.findAllByIds(fileIds).associate {
            val id = requireNotNull(it.id)
            id to ImageFileInfo(id, urlOf(it.storageKey), it.uploaderMemberId, it.isImage)
        }
    }

    fun urlOf(storageKey: String): String = "${properties.baseUrl}/files/$storageKey"
}
