package com.classplatform.course.domain

enum class CourseStatus {
	DRAFT,
	PUBLISHED,
	CLOSED,
	;

	fun canTransitionTo(target: CourseStatus): Boolean = when (this) {
		DRAFT -> target == PUBLISHED
		PUBLISHED -> target == CLOSED
		CLOSED -> false
	}
}
