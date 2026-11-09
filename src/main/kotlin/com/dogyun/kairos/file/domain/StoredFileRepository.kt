package com.dogyun.kairos.file.domain

interface StoredFileRepository {
    fun save(file: StoredFile): StoredFile
    fun findAllByIds(ids: Collection<Long>): List<StoredFile>
}
