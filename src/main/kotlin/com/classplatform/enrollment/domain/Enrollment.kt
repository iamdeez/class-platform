package com.classplatform.enrollment.domain

import com.classplatform.common.UserId
import com.classplatform.enrollment.domain.exception.InvalidEnrollmentStatusException

class Enrollment private constructor(
	val id: Long?,
	val courseId: Long,
	val userId: UserId,
	status: EnrollmentStatus,
) {
	var status: EnrollmentStatus = status
		private set

	fun cancel() {
		if (status == EnrollmentStatus.CANCELLED) {
			throw InvalidEnrollmentStatusException("enrollment is already cancelled")
		}
		status = EnrollmentStatus.CANCELLED
	}

	companion object {
		fun create(courseId: Long, userId: UserId): Enrollment =
			Enrollment(id = null, courseId = courseId, userId = userId, status = EnrollmentStatus.ACTIVE)

		fun reconstitute(id: Long, courseId: Long, userId: UserId, status: EnrollmentStatus): Enrollment =
			Enrollment(id, courseId, userId, status)
	}
}
