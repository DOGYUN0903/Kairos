package com.dogyun.kairos.file.presentation

import com.dogyun.kairos.common.presentation.ApiResponse
import com.dogyun.kairos.common.security.LoginMember
import com.dogyun.kairos.file.application.FileService
import com.dogyun.kairos.file.application.UploadResult
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/files")
class FileController(private val fileService: FileService) {

    @PostMapping("/images")
    fun uploadImage(
        @AuthenticationPrincipal loginMember: LoginMember,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<ApiResponse<UploadResult>> =
        ApiResponse.created(
            fileService.uploadImage(loginMember.id, file.originalFilename, file.contentType, file.bytes),
        )
}
