package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.course.domain.exception.CourseNotFoundException
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.exception.EnrollmentAccessDeniedException
import com.classplatform.enrollment.domain.exception.EnrollmentCancellationNotAllowedException
import com.classplatform.enrollment.domain.exception.EnrollmentNotFoundException
import org.springframework.stereotype.Service

@Service
class CancelEnrollmentUseCase(
	private val enrollmentRepository: EnrollmentRepository,
	private val courseRepository: CourseRepository,
) {
	fun execute(enrollmentId: Long, requesterId: UserId): Enrollment {
		val enrollment = enrollmentRepository.findById(enrollmentId)
			?: throw EnrollmentNotFoundException("enrollment not found: $enrollmentId")
		if (enrollment.userId != requesterId) {
			throw EnrollmentAccessDeniedException("user $requesterId cannot cancel enrollment $enrollmentId")
		}
		val course = courseRepository.findById(enrollment.courseId)
			?: throw CourseNotFoundException("course not found: ${enrollment.courseId}")
		if (course.status == CourseStatus.CLOSED) {
			throw EnrollmentCancellationNotAllowedException(
				"cannot cancel enrollment for a closed course: ${enrollment.courseId}",
			)
		}
		enrollment.cancel()
		return enrollmentRepository.save(enrollment)
	}
}
