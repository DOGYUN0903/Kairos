package com.dogyun.kairos.catalog.application

/** file 모듈의 이미지 파일 정보를 읽는 포트. 구현은 catalog.infrastructure 가 맡는다. */
interface ImageFileReader {
    /** 존재하지 않는 ID는 결과에서 빠진다. */
    fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef>
}

/** catalog 가 보는 이미지 파일 정보. seller 모듈의 타입과는 별개다. */
data class ImageFileRef(
    val fileId: Long,
    val url: String,
    val uploaderMemberId: Long,
    val isImage: Boolean,
)
