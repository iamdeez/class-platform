package com.classplatform.course.application

import com.classplatform.common.PageRequest
import com.classplatform.common.PageResult
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import org.springframework.stereotype.Service

@Service
class ListCoursesUseCase(
	private val courseRepository: CourseRepository,
) {
	fun execute(pageRequest: PageRequest): PageResult<Course> =
		courseRepository.findAllByStatus(CourseStatus.PUBLISHED, pageRequest)
}
