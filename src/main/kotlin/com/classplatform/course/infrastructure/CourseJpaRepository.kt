package com.classplatform.course.infrastructure

import com.classplatform.course.domain.CourseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CourseJpaRepository : JpaRepository<CourseJpaEntity, Long> {
	fun findAllByStatus(status: CourseStatus, pageable: Pageable): Page<CourseJpaEntity>

	fun findAllByInstructorId(instructorId: Long): List<CourseJpaEntity>
}
