package com.classplatform.enrollment.infrastructure

import com.classplatform.enrollment.domain.EnrollmentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "enrollment")
class EnrollmentJpaEntity(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long?,

	@Column(name = "course_id", nullable = false)
	val courseId: Long,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	var status: EnrollmentStatus,
) {
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	var updatedAt: LocalDateTime? = null
}
