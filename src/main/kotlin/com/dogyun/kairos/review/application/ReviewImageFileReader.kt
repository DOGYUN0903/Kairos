package com.dogyun.kairos.review.application

/** file 모듈의 이미지 조회 포트(catalog 의 ImageFileReader 와 같은 모양, 별개 타입). */
interface ReviewImageFileReader {
    /** 존재하지 않는 ID는 결과에서 빠진다. */
    fun findImages(fileIds: Collection<Long>): Map<Long, ReviewImageRef>
}

data class ReviewImageRef(
    val fileId: Long,
    val url: String,
    val uploaderMemberId: Long,
    val isImage: Boolean,
)
