package com.classplatform.enrollment.domain

enum class EnrollmentStatus {
	ACTIVE,
	COMPLETED,
	CANCELLED,
	;

	fun canTransitionTo(target: EnrollmentStatus): Boolean = when (this) {
		ACTIVE -> target == COMPLETED || target == CANCELLED
		COMPLETED -> false
		CANCELLED -> false
	}
}
