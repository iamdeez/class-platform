package com.classplatform.common

import com.classplatform.common.exception.DuplicateResourceException
import com.classplatform.common.exception.ForbiddenActionException
import com.classplatform.common.exception.InvalidStateException
import com.classplatform.common.exception.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException::class)
	fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("NOT_FOUND", ex.message.orEmpty()))

	@ExceptionHandler(DuplicateResourceException::class)
	fun handleDuplicate(ex: DuplicateResourceException): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("CONFLICT", ex.message.orEmpty()))

	@ExceptionHandler(InvalidStateException::class)
	fun handleInvalidState(ex: InvalidStateException): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("INVALID_STATE", ex.message.orEmpty()))

	@ExceptionHandler(ForbiddenActionException::class)
	fun handleForbidden(ex: ForbiddenActionException): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("FORBIDDEN", ex.message.orEmpty()))

	@ExceptionHandler(IllegalArgumentException::class)
	fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> =
		ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("BAD_REQUEST", ex.message.orEmpty()))

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
		val message = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("VALIDATION_FAILED", message))
	}
}
