package com.classplatform.course.application

import com.classplatform.common.HtmlSanitizer
import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RegisterCourseUseCase(
	private val courseRepository: CourseRepository,
) {
	fun execute(title: String, description: String?, price: BigDecimal, instructorId: UserId): Course {
		val course = Course.register(title, description?.let(HtmlSanitizer::sanitize), price, instructorId)
		return courseRepository.save(course)
	}
}
