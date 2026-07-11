package com.classplatform.enrollment.domain

import com.classplatform.common.UserId
import com.classplatform.enrollment.domain.exception.InvalidEnrollmentStatusException
import java.math.BigDecimal

class Enrollment private constructor(
	val id: Long?,
	val courseId: Long,
	val userId: UserId,
	val price: BigDecimal,
	status: EnrollmentStatus,
) {
	var status: EnrollmentStatus = status
		private set

	fun cancel() = transitionTo(EnrollmentStatus.CANCELLED)

	fun complete() = transitionTo(EnrollmentStatus.COMPLETED)

	private fun transitionTo(target: EnrollmentStatus) {
		if (!status.canTransitionTo(target)) {
			throw InvalidEnrollmentStatusException(
				"cannot transition enrollment status from $status to $target",
			)
		}
		status = target
	}

	companion object {
		fun create(courseId: Long, userId: UserId, price: BigDecimal): Enrollment =
			Enrollment(id = null, courseId = courseId, userId = userId, price = price, status = EnrollmentStatus.ACTIVE)

		fun reconstitute(
			id: Long,
			courseId: Long,
			userId: UserId,
			price: BigDecimal,
			status: EnrollmentStatus,
		): Enrollment = Enrollment(id, courseId, userId, price, status)
	}
}
