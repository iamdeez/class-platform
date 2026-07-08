package com.classplatform.course.infrastructure

import com.classplatform.course.domain.CourseStatus
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
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "course")
class CourseJpaEntity(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long?,

	@Column(nullable = false, length = 200)
	var title: String,

	@Column(columnDefinition = "TEXT")
	var description: String?,

	@Column(nullable = false, precision = 10, scale = 2)
	var price: BigDecimal,

	@Column(name = "instructor_id", nullable = false)
	var instructorId: Long,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	var status: CourseStatus,
) {
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: LocalDateTime? = null

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	var updatedAt: LocalDateTime? = null
}
