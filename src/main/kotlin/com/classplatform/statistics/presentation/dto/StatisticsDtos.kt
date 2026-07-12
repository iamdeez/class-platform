package com.classplatform.statistics.presentation.dto

import java.math.BigDecimal

data class CourseStatisticsResponse(
	val courseId: Long,
	val title: String,
	val studentCount: Long,
	val revenue: BigDecimal,
	val completionRate: Double,
	val cancellationRate: Double,
)

data class CourseStatisticsListResponse(val items: List<CourseStatisticsResponse>)
