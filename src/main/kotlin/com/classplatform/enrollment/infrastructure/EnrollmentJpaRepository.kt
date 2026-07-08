package com.classplatform.enrollment.infrastructure

import org.springframework.data.jpa.repository.JpaRepository

interface EnrollmentJpaRepository : JpaRepository<EnrollmentJpaEntity, Long> {
	fun findAllByUserId(userId: Long): List<EnrollmentJpaEntity>
}
