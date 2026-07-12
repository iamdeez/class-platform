package com.classplatform.statistics.presentation

import com.classplatform.common.ApiResponse
import com.classplatform.common.UserId
import com.classplatform.statistics.application.GetCourseStatisticsUseCase
import com.classplatform.statistics.application.GetInstructorStatisticsUseCase
import com.classplatform.statistics.domain.CourseStatistics
import com.classplatform.statistics.presentation.dto.CourseStatisticsListResponse
import com.classplatform.statistics.presentation.dto.CourseStatisticsResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class StatisticsController(
	private val getInstructorStatisticsUseCase: GetInstructorStatisticsUseCase,
	private val getCourseStatisticsUseCase: GetCourseStatisticsUseCase,
) {

	@GetMapping("/api/instructors/me/statistics")
	fun getInstructorStatistics(
		@RequestHeader("X-User-Id") userId: Long,
	): ResponseEntity<ApiResponse<CourseStatisticsListResponse>> {
		val statistics = getInstructorStatisticsUseCase.execute(UserId(userId))
		return ResponseEntity.ok(
			ApiResponse.success(CourseStatisticsListResponse(statistics.map { it.toResponse() })),
		)
	}

	@GetMapping("/api/courses/{courseId}/statistics")
	fun getCourseStatistics(
		@PathVariable courseId: Long,
		@RequestHeader("X-User-Id") userId: Long,
	): ResponseEntity<ApiResponse<CourseStatisticsResponse>> {
		val statistics = getCourseStatisticsUseCase.execute(courseId, UserId(userId))
		return ResponseEntity.ok(ApiResponse.success(statistics.toResponse()))
	}

	private fun CourseStatistics.toResponse() = CourseStatisticsResponse(
		courseId = courseId,
		title = title,
		studentCount = studentCount,
		revenue = revenue,
		completionRate = completionRate,
		cancellationRate = cancellationRate,
	)
}
