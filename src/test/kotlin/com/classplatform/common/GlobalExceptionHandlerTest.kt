package com.classplatform.common

import com.classplatform.common.exception.DuplicateResourceException
import com.classplatform.common.exception.ForbiddenActionException
import com.classplatform.common.exception.InvalidStateException
import com.classplatform.common.exception.ResourceNotFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {

	private val handler = GlobalExceptionHandler()

	@Test
	fun `ResourceNotFoundException은 404로 매핑된다`() {
		val response = handler.handleNotFound(object : ResourceNotFoundException("찾을 수 없음") {})
		assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
		assertEquals("NOT_FOUND", response.body?.error?.code)
	}

	@Test
	fun `DuplicateResourceException은 409로 매핑된다`() {
		val response = handler.handleDuplicate(object : DuplicateResourceException("중복") {})
		assertEquals(HttpStatus.CONFLICT, response.statusCode)
		assertEquals("CONFLICT", response.body?.error?.code)
	}

	@Test
	fun `InvalidStateException은 409로 매핑된다`() {
		val response = handler.handleInvalidState(object : InvalidStateException("잘못된 상태") {})
		assertEquals(HttpStatus.CONFLICT, response.statusCode)
		assertEquals("INVALID_STATE", response.body?.error?.code)
	}

	@Test
	fun `ForbiddenActionException은 403으로 매핑된다`() {
		val response = handler.handleForbidden(object : ForbiddenActionException("권한 없음") {})
		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals("FORBIDDEN", response.body?.error?.code)
	}

	@Test
	fun `IllegalArgumentException은 400으로 매핑된다`() {
		val response = handler.handleIllegalArgument(IllegalArgumentException("잘못된 입력"))
		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
		assertEquals("BAD_REQUEST", response.body?.error?.code)
	}

	@Test
	fun `MethodArgumentNotValidException은 400으로 매핑되고 필드 오류를 담는다`() {
		val bindingResult = mockk<BindingResult>()
		every { bindingResult.fieldErrors } returns listOf(FieldError("target", "title", "must not be blank"))
		val ex = mockk<MethodArgumentNotValidException>()
		every { ex.bindingResult } returns bindingResult

		val response = handler.handleValidation(ex)

		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
		assertEquals("VALIDATION_FAILED", response.body?.error?.code)
		assertEquals("title: must not be blank", response.body?.error?.message)
	}
}
