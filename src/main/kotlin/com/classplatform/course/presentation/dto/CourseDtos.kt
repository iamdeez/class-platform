package com.classplatform.course.presentation.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class RegisterCourseRequest(
	@field:NotBlank
	val title: String,
	val description: String?,
	@field:NotNull
	@field:DecimalMin(value = "0", inclusive = true)
	val price: BigDecimal,
)

data class RegisterCourseResponse(val courseId: Long)

data class CourseResponse(
	val id: Long,
	val title: String,
	val description: String?,
	val price: BigDecimal,
	val instructorId: Long,
	val status: String,
)

data class CourseListResponse(
	val items: List<CourseResponse>,
	val page: Int,
	val totalCount: Long,
)

enum class CourseStatusAction { PUBLISH, CLOSE }

data class CourseStatusUpdateRequest(
	@field:NotNull
	val action: CourseStatusAction,
)
