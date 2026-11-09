package com.dogyun.kairos.file.application

/** 파일 바이트를 저장하는 포트. 로컬 디스크 구현은 infrastructure 에 있고, 이후 S3 등으로 바꿀 수 있다. */
interface ImageStorage {
    fun store(storageKey: String, bytes: ByteArray, contentType: String)
}
