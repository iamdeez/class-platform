package com.classplatform.course.domain

import com.classplatform.common.UserId
import com.classplatform.course.domain.exception.InvalidCourseStatusTransitionException
import java.math.BigDecimal

class Course private constructor(
	val id: Long?,
	val title: String,
	val description: String?,
	val price: BigDecimal,
	val instructorId: UserId,
	status: CourseStatus,
) {
	var status: CourseStatus = status
		private set

	init {
		require(title.isNotBlank()) { "title must not be blank" }
		require(price >= BigDecimal.ZERO) { "price must not be negative: $price" }
	}

	fun publish() = transitionTo(CourseStatus.PUBLISHED)

	fun close() = transitionTo(CourseStatus.CLOSED)

	private fun transitionTo(target: CourseStatus) {
		if (!status.canTransitionTo(target)) {
			throw InvalidCourseStatusTransitionException(
				"cannot transition course status from $status to $target",
			)
		}
		status = target
	}

	companion object {
		fun register(
			title: String,
			description: String?,
			price: BigDecimal,
			instructorId: UserId,
		): Course = Course(
			id = null,
			title = title,
			description = description,
			price = price,
			instructorId = instructorId,
			status = CourseStatus.DRAFT,
		)

		fun reconstitute(
			id: Long,
			title: String,
			description: String?,
			price: BigDecimal,
			instructorId: UserId,
			status: CourseStatus,
		): Course = Course(id, title, description, price, instructorId, status)
	}
}
