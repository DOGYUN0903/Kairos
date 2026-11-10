package com.dogyun.kairos.review.infrastructure

import com.dogyun.kairos.file.application.FileQueryService
import com.dogyun.kairos.review.application.ReviewImageFileReader
import com.dogyun.kairos.review.application.ReviewImageRef
import org.springframework.stereotype.Component

@Component
class ReviewImageFileReaderImpl(private val fileQueryService: FileQueryService) : ReviewImageFileReader {
    override fun findImages(fileIds: Collection<Long>): Map<Long, ReviewImageRef> =
        fileQueryService.findImages(fileIds).mapValues { (_, info) ->
            ReviewImageRef(info.fileId, info.url, info.uploaderMemberId, info.isImage)
        }
}
