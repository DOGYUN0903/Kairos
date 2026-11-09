package com.dogyun.kairos.file.application

import com.dogyun.kairos.file.domain.StoredFile
import com.dogyun.kairos.file.domain.StoredFileRepository
import com.dogyun.kairos.file.application.FileProperties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileServiceTest {

    private class FakeStoredFileRepository : StoredFileRepository {
        val store = linkedMapOf<Long, StoredFile>()
        private var sequence = 0L

        override fun save(file: StoredFile): StoredFile {
            val saved = StoredFile(++sequence, file.storageKey, file.originalName, file.contentType, file.sizeBytes, file.uploaderMemberId)
            store[sequence] = saved
            return saved
        }

        override fun findAllByIds(ids: Collection<Long>): List<StoredFile> = ids.mapNotNull { store[it] }
    }

    private class FakeImageStorage : ImageStorage {
        val stored = mutableListOf<Triple<String, ByteArray, String>>()
        override fun store(storageKey: String, bytes: ByteArray, contentType: String) {
            stored += Triple(storageKey, bytes, contentType)
        }
    }

    private val repository = FakeStoredFileRepository()
    private val storage = FakeImageStorage()
    private val queryService = FileQueryService(repository, FileProperties("unused", "http://cdn.test"))
    private val service = FileService(repository, storage, queryService)

    @Test
    fun 업로드하면_저장_키가_images_UUID_확장자이고_URL이_baseUrl_files_키다() {
        val bytes = byteArrayOf(1, 2, 3)

        val result = service.uploadImage(7L, "a.png", "image/png", bytes)

        val (key, storedBytes, type) = storage.stored.single()
        assertTrue(Regex("images/[0-9a-f-]{36}\\.png").matches(key))
        assertContentEquals(bytes, storedBytes)
        assertEquals("image/png", type)
        assertEquals("http://cdn.test/files/$key", result.url)
        assertEquals(7L, repository.store.getValue(result.fileId).uploaderMemberId)
    }

    @Test
    fun findImages는_없는_ID를_뺀다() {
        val a = service.uploadImage(7L, "a.png", "image/png", byteArrayOf(1))

        val found = queryService.findImages(listOf(a.fileId, 999L))

        assertEquals(setOf(a.fileId), found.keys)
        assertEquals(7L, found.getValue(a.fileId).uploaderMemberId)
        assertTrue(found.getValue(a.fileId).isImage)
    }
}
