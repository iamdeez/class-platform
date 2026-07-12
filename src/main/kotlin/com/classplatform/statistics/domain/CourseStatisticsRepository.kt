package com.classplatform.statistics.domain

import com.classplatform.common.UserId

interface CourseStatisticsRepository {
	fun findAllByInstructorId(instructorId: UserId): List<CourseStatistics>

	fun findByCourseId(courseId: Long): CourseStatistics?
}
