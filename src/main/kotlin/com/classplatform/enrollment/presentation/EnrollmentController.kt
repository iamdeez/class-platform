package com.classplatform.enrollment.presentation

import com.classplatform.common.ApiResponse
import com.classplatform.common.UserId
import com.classplatform.enrollment.application.CancelEnrollmentUseCase
import com.classplatform.enrollment.application.EnrollUseCase
import com.classplatform.enrollment.application.ListMyEnrollmentsUseCase
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.presentation.dto.EnrollmentListResponse
import com.classplatform.enrollment.presentation.dto.EnrollmentResponse
import com.classplatform.enrollment.presentation.dto.RegisterEnrollmentResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class EnrollmentController(
	private val enrollUseCase: EnrollUseCase,
	private val cancelEnrollmentUseCase: CancelEnrollmentUseCase,
	private val listMyEnrollmentsUseCase: ListMyEnrollmentsUseCase,
) {

	@PostMapping("/api/courses/{courseId}/enrollments")
	fun enroll(
		@PathVariable courseId: Long,
		@RequestHeader("X-User-Id") userId: Long,
	): ResponseEntity<ApiResponse<RegisterEnrollmentResponse>> {
		val enrollment = enrollUseCase.execute(courseId, UserId(userId))
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(RegisterEnrollmentResponse(requireNotNull(enrollment.id))))
	}

	@DeleteMapping("/api/enrollments/{enrollmentId}")
	fun cancel(
		@PathVariable enrollmentId: Long,
		@RequestHeader("X-User-Id") userId: Long,
	): ResponseEntity<Void> {
		cancelEnrollmentUseCase.execute(enrollmentId, UserId(userId))
		return ResponseEntity.noContent().build()
	}

	@GetMapping("/api/users/me/enrollments")
	fun listMine(
		@RequestHeader("X-User-Id") userId: Long,
	): ResponseEntity<ApiResponse<EnrollmentListResponse>> {
		val enrollments = listMyEnrollmentsUseCase.execute(UserId(userId))
		return ResponseEntity.ok(
			ApiResponse.success(EnrollmentListResponse(enrollments.map { it.toResponse() })),
		)
	}

	private fun Enrollment.toResponse() = EnrollmentResponse(
		id = requireNotNull(id),
		courseId = courseId,
		userId = userId.value,
		status = status.name,
	)
}
