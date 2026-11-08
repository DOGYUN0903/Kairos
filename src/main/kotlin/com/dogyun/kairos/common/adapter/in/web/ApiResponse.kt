package com.dogyun.kairos.common.adapter.`in`.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

data class ApiResponse<T>(
    val message: String,
    val data: T?,
    val timestamp: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun <T> ok(data: T, message: String = "success"): ResponseEntity<ApiResponse<T>> =
            ResponseEntity.ok(ApiResponse(message, data))

        fun <T> created(data: T, message: String = "success"): ResponseEntity<ApiResponse<T>> =
            ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse(message, data))

        fun noContent(): ResponseEntity<Void> = ResponseEntity.noContent().build()
    }
}
