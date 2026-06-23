package com.orderprocessing.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

data class ErrorResponse(
        val timestamp: Instant = Instant.now(),
        val status: Int,
        val error: String,
        val message: String,
        val path: String? = null
)

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(e: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Resource not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(
                        status = HttpStatus.NOT_FOUND.value(),
                        error = "Not Found",
                        message = e.message ?: "Resource not found"
                )
        )
    }

    @ExceptionHandler(InsufficientStockException::class)
    fun handleInsufficientStock(e: InsufficientStockException): ResponseEntity<ErrorResponse> {
        log.warn("Insufficient stock: {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(
                        status = HttpStatus.CONFLICT.value(),
                        error = "Insufficient Stock",
                        message = e.message ?: "Insufficient stock"
                )
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> {
        log.warn("Illegal state: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(
                        status = HttpStatus.BAD_REQUEST.value(),
                        error = "Bad Request",
                        message = e.message ?: "Illegal state"
                )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = e.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: {}", errors)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(
                        status = HttpStatus.BAD_REQUEST.value(),
                        error = "Validation Failed",
                        message = errors
                )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error: {}", e.message, e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse(
                        status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        error = "Internal Server Error",
                        message = "An unexpected error occurred"
                )
        )
    }
}