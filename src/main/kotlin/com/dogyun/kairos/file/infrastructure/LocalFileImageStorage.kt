package com.dogyun.kairos.file.infrastructure

import com.dogyun.kairos.file.application.FileProperties
import com.dogyun.kairos.file.application.ImageStorage
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class LocalFileImageStorage(private val properties: FileProperties) : ImageStorage {

    override fun store(storageKey: String, bytes: ByteArray, contentType: String) {
        val root = Path.of(properties.localDir).toAbsolutePath().normalize()
        val target = root.resolve(storageKey).normalize()
        require(target.startsWith(root)) { "저장 경로가 올바르지 않습니다." }
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }
}
