package com.dogyun.kairos.common.presentation

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(e: DomainException): ResponseEntity<ErrorResponse> =
        error(e.errorCode, e.message ?: e.errorCode.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val detail = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return error(ErrorCode.INVALID_REQUEST, detail.ifBlank { ErrorCode.INVALID_REQUEST.message })
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        error(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.message)

    @ExceptionHandler(
        MethodArgumentTypeMismatchException::class,
        MissingServletRequestParameterException::class,
        HandlerMethodValidationException::class,
        ConstraintViolationException::class,
    )
    fun handleBadRequest(e: Exception): ResponseEntity<ErrorResponse> =
        error(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.message)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        error(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.message)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        error(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.message)

    /**
     * 위에서 개별 처리하지 않은 Spring MVC 프레임워크 예외(415, 406, 필수 헤더 누락, 업로드 크기 초과 등).
     * 프레임워크가 정한 상태 코드를 유지하고 본문만 ErrorResponse로 맞춘다.
     * 5xx(예: MissingPathVariableException)는 서버 결함이므로 500 INTERNAL_ERROR로 처리한다.
     */
    private fun handleFrameworkError(
        e: org.springframework.web.ErrorResponse,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val status = e.statusCode
        if (status.is5xxServerError) {
            log.error("프레임워크 서버 예외 {} {}", request.method, request.requestURI, e as Throwable)
            return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message)
        }
        log.warn("클라이언트 오류 {} {} -> {}: {}", request.method, request.requestURI, status.value(), (e as Throwable).message)
        return ResponseEntity.status(status)
            .headers(e.headers)
            .body(ErrorResponse(ErrorCode.INVALID_REQUEST.name, ErrorCode.INVALID_REQUEST.message))
    }

    /** 업로드 크기 초과는 ErrorResponse를 구현하지 않으므로 별도로 413으로 매핑한다. */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(e: MaxUploadSizeExceededException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("클라이언트 오류 {} {} -> 413: {}", request.method, request.requestURI, e.message)
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
            .body(ErrorResponse(ErrorCode.INVALID_REQUEST.name, ErrorCode.INVALID_REQUEST.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        if (e is org.springframework.web.ErrorResponse) return handleFrameworkError(e, request)
        log.error("처리되지 않은 예외 {} {}", request.method, request.requestURI, e)
        return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message)
    }

    private companion object {
        val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    private fun error(code: ErrorCode, message: String): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(code.httpStatus).body(ErrorResponse(code.name, message))
}
