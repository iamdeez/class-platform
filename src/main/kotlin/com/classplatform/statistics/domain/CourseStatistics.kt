package com.classplatform.statistics.domain

import java.math.BigDecimal

data class CourseStatistics(
	val courseId: Long,
	val title: String,
	val studentCount: Long,
	val revenue: BigDecimal,
	val completionRate: Double,
	val cancellationRate: Double,
)
