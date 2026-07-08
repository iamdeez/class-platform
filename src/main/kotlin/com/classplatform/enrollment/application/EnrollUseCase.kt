package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.course.domain.exception.CourseNotEnrollableException
import com.classplatform.course.domain.exception.CourseNotFoundException
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import org.springframework.stereotype.Service

@Service
class EnrollUseCase(
	private val enrollmentRepository: EnrollmentRepository,
	private val courseRepository: CourseRepository,
) {
	fun execute(courseId: Long, userId: UserId): Enrollment {
		val course = courseRepository.findById(courseId)
			?: throw CourseNotFoundException("course not found: $courseId")
		if (course.status != CourseStatus.PUBLISHED) {
			throw CourseNotEnrollableException("course is not enrollable: $courseId (status=${course.status})")
		}
		val enrollment = Enrollment.create(courseId, userId)
		return enrollmentRepository.save(enrollment)
	}
}
