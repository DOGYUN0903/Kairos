package com.dogyun.kairos.file.application

data class UploadResult(val fileId: Long, val url: String)

/** 다른 모듈이 이미지 파일을 검증할 때 쓰는 결과. 도메인 타입을 노출하지 않는다. */
data class ImageFileInfo(
    val fileId: Long,
    val url: String,
    val uploaderMemberId: Long,
    val isImage: Boolean,
)
