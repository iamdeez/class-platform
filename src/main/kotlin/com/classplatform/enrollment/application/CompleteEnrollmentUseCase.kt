package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.exception.CourseNotFoundException
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.exception.EnrollmentAccessDeniedException
import com.classplatform.enrollment.domain.exception.EnrollmentNotFoundException
import org.springframework.stereotype.Service

@Service
class CompleteEnrollmentUseCase(
	private val enrollmentRepository: EnrollmentRepository,
	private val courseRepository: CourseRepository,
) {
	fun execute(enrollmentId: Long, requesterId: UserId): Enrollment {
		val enrollment = enrollmentRepository.findById(enrollmentId)
			?: throw EnrollmentNotFoundException("enrollment not found: $enrollmentId")
		val course = courseRepository.findById(enrollment.courseId)
			?: throw CourseNotFoundException("course not found: ${enrollment.courseId}")
		if (course.instructorId != requesterId) {
			throw EnrollmentAccessDeniedException("user $requesterId cannot complete enrollment $enrollmentId")
		}
		enrollment.complete()
		return enrollmentRepository.save(enrollment)
	}
}
