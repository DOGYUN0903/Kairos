package com.dogyun.kairos.seller.infrastructure

import com.dogyun.kairos.file.application.FileQueryService
import com.dogyun.kairos.seller.application.ImageFileReader
import com.dogyun.kairos.seller.application.ImageFileRef
import org.springframework.stereotype.Component

/** seller 의 [ImageFileReader] 를 file 모듈의 조회 서비스로 구현한다. */
@Component
class ImageFileReaderImpl(private val fileQueryService: FileQueryService) : ImageFileReader {

    override fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> =
        fileQueryService.findImages(fileIds).mapValues { (_, info) ->
            ImageFileRef(info.fileId, info.url, info.uploaderMemberId, info.isImage)
        }
}
