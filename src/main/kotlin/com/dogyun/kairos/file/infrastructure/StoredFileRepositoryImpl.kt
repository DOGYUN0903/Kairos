package com.dogyun.kairos.file.infrastructure

import com.dogyun.kairos.file.domain.StoredFile
import com.dogyun.kairos.file.domain.StoredFileRepository
import org.springframework.stereotype.Repository

@Repository
class StoredFileRepositoryImpl(private val jpaRepository: StoredFileJpaRepository) : StoredFileRepository {

    // 파일은 한번 저장하면 바뀌지 않으므로 신규 저장만 있다.
    override fun save(file: StoredFile): StoredFile = toDomain(
        jpaRepository.save(
            StoredFileEntity(file.storageKey, file.originalName, file.contentType, file.sizeBytes, file.uploaderMemberId),
        ),
    )

    override fun findAllByIds(ids: Collection<Long>): List<StoredFile> =
        jpaRepository.findAllById(ids).map(::toDomain)

    private fun toDomain(e: StoredFileEntity) =
        StoredFile(e.id, e.storageKey, e.originalName, e.contentType, e.sizeBytes, e.uploaderMemberId)
}
