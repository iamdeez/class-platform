package com.classplatform.statistics.application

import com.classplatform.common.UserId
import com.classplatform.statistics.domain.CourseStatistics
import com.classplatform.statistics.domain.CourseStatisticsRepository
import org.springframework.stereotype.Service

@Service
class GetInstructorStatisticsUseCase(
	private val courseStatisticsRepository: CourseStatisticsRepository,
) {
	fun execute(instructorId: UserId): List<CourseStatistics> =
		courseStatisticsRepository.findAllByInstructorId(instructorId)
}
