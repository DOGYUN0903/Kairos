package com.dogyun.kairos.catalog.infrastructure

import com.dogyun.kairos.catalog.application.ImageFileReader
import com.dogyun.kairos.catalog.application.ImageFileRef
import com.dogyun.kairos.file.application.FileQueryService
import org.springframework.stereotype.Component

/** catalog 의 [ImageFileReader] 를 file 모듈의 조회 서비스로 구현한다. */
@Component("catalogImageFileReader") // seller 모듈의 같은 이름 클래스와 빈 이름이 겹치지 않도록 한다.
class ImageFileReaderImpl(private val fileQueryService: FileQueryService) : ImageFileReader {

    override fun findImages(fileIds: Collection<Long>): Map<Long, ImageFileRef> =
        fileQueryService.findImages(fileIds).mapValues { (_, info) ->
            ImageFileRef(info.fileId, info.url, info.uploaderMemberId, info.isImage)
        }
}
