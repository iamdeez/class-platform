package com.classplatform.enrollment.domain

import com.classplatform.common.UserId

interface EnrollmentRepository {
	fun save(enrollment: Enrollment): Enrollment

	fun findById(id: Long): Enrollment?

	fun findAllByUserId(userId: UserId): List<Enrollment>
}
