package com.dogyun.kairos.review.domain

/** 후기 사진. file 모듈의 파일을 ID 로만 참조한다. */
data class ReviewImage(val fileId: Long, val displayOrder: Int)
