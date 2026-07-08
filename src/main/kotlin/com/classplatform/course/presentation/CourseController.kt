package com.classplatform.course.presentation

import com.classplatform.common.ApiResponse
import com.classplatform.common.PageRequest
import com.classplatform.common.UserId
import com.classplatform.course.application.CloseCourseUseCase
import com.classplatform.course.application.GetCourseUseCase
import com.classplatform.course.application.ListCoursesUseCase
import com.classplatform.course.application.PublishCourseUseCase
import com.classplatform.course.application.RegisterCourseUseCase
import com.classplatform.course.domain.Course
import com.classplatform.course.presentation.dto.CourseListResponse
import com.classplatform.course.presentation.dto.CourseResponse
import com.classplatform.course.presentation.dto.CourseStatusAction
import com.classplatform.course.presentation.dto.CourseStatusUpdateRequest
import com.classplatform.course.presentation.dto.RegisterCourseRequest
import com.classplatform.course.presentation.dto.RegisterCourseResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/courses")
class CourseController(
	private val registerCourseUseCase: RegisterCourseUseCase,
	private val getCourseUseCase: GetCourseUseCase,
	private val listCoursesUseCase: ListCoursesUseCase,
	private val publishCourseUseCase: PublishCourseUseCase,
	private val closeCourseUseCase: CloseCourseUseCase,
) {

	@PostMapping
	fun register(
		@RequestHeader("X-User-Id") userId: Long,
		@Valid @RequestBody request: RegisterCourseRequest,
	): ResponseEntity<ApiResponse<RegisterCourseResponse>> {
		val course = registerCourseUseCase.execute(
			title = request.title,
			description = request.description,
			price = request.price,
			instructorId = UserId(userId),
		)
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(RegisterCourseResponse(requireNotNull(course.id))))
	}

	@GetMapping
	fun list(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ResponseEntity<ApiResponse<CourseListResponse>> {
		val result = listCoursesUseCase.execute(PageRequest(page, size))
		return ResponseEntity.ok(
			ApiResponse.success(
				CourseListResponse(
					items = result.items.map { it.toResponse() },
					page = result.page,
					totalCount = result.totalCount,
				),
			),
		)
	}

	@GetMapping("/{courseId}")
	fun get(@PathVariable courseId: Long): ResponseEntity<ApiResponse<CourseResponse>> {
		val course = getCourseUseCase.execute(courseId)
		return ResponseEntity.ok(ApiResponse.success(course.toResponse()))
	}

	@PatchMapping("/{courseId}/status")
	fun updateStatus(
		@PathVariable courseId: Long,
		@Valid @RequestBody request: CourseStatusUpdateRequest,
	): ResponseEntity<ApiResponse<CourseResponse>> {
		val course = when (request.action) {
			CourseStatusAction.PUBLISH -> publishCourseUseCase.execute(courseId)
			CourseStatusAction.CLOSE -> closeCourseUseCase.execute(courseId)
		}
		return ResponseEntity.ok(ApiResponse.success(course.toResponse()))
	}

	private fun Course.toResponse() = CourseResponse(
		id = requireNotNull(id),
		title = title,
		description = description,
		price = price,
		instructorId = instructorId.value,
		status = status.name,
	)
}
