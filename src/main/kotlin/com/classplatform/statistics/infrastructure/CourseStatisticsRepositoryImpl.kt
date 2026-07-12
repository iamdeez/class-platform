package com.classplatform.statistics.infrastructure

import com.classplatform.common.UserId
import com.classplatform.statistics.domain.CourseStatistics
import com.classplatform.statistics.domain.CourseStatisticsRepository
import org.springframework.stereotype.Repository

@Repository
class CourseStatisticsRepositoryImpl(
	private val mapper: CourseStatisticsMapper,
) : CourseStatisticsRepository {

	override fun findAllByInstructorId(instructorId: UserId): List<CourseStatistics> =
		mapper.findAllByInstructorId(instructorId.value).map { it.toDomain() }

	override fun findByCourseId(courseId: Long): CourseStatistics? =
		mapper.findByCourseId(courseId)?.toDomain()

	private fun CourseStatisticsRow.toDomain(): CourseStatistics = CourseStatistics(
		courseId = courseId,
		title = title,
		studentCount = studentCount,
		revenue = revenue,
		completionRate = safeDivide(completedCount, studentCount),
		cancellationRate = safeDivide(cancelledCount, totalCount),
	)

	private fun safeDivide(numerator: Long, denominator: Long): Double =
		if (denominator == 0L) 0.0 else numerator.toDouble() / denominator.toDouble()
}
