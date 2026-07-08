package com.classplatform.course.application

import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.exception.CourseNotFoundException
import org.springframework.stereotype.Service

@Service
class GetCourseUseCase(
	private val courseRepository: CourseRepository,
) {
	fun execute(courseId: Long): Course =
		courseRepository.findById(courseId) ?: throw CourseNotFoundException("course not found: $courseId")
}
