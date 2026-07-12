package com.classplatform.statistics.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.exception.CourseAccessDeniedException
import com.classplatform.course.domain.exception.CourseNotFoundException
import com.classplatform.statistics.domain.CourseStatistics
import com.classplatform.statistics.domain.CourseStatisticsRepository
import org.springframework.stereotype.Service

@Service
class GetCourseStatisticsUseCase(
	private val courseRepository: CourseRepository,
	private val courseStatisticsRepository: CourseStatisticsRepository,
) {
	fun execute(courseId: Long, requesterId: UserId): CourseStatistics {
		val course = courseRepository.findById(courseId)
			?: throw CourseNotFoundException("course not found: $courseId")
		if (course.instructorId != requesterId) {
			throw CourseAccessDeniedException("user $requesterId cannot view statistics of course $courseId")
		}
		return courseStatisticsRepository.findByCourseId(courseId)
			?: throw CourseNotFoundException("course not found: $courseId")
	}
}
