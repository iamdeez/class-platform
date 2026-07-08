package com.classplatform.enrollment.presentation.dto

data class RegisterEnrollmentResponse(val enrollmentId: Long)

data class EnrollmentResponse(
	val id: Long,
	val courseId: Long,
	val userId: Long,
	val status: String,
)

data class EnrollmentListResponse(val items: List<EnrollmentResponse>)
