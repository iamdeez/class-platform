package com.classplatform.course.infrastructure

import com.classplatform.common.PageResult
import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import org.springframework.data.domain.PageRequest as SpringPageRequest
import org.springframework.stereotype.Repository
import com.classplatform.common.PageRequest as DomainPageRequest

@Repository
class CourseRepositoryImpl(
	private val jpaRepository: CourseJpaRepository,
) : CourseRepository {

	override fun save(course: Course): Course {
		val entity = course.toEntity()
		val saved = jpaRepository.save(entity)
		return saved.toDomain()
	}

	override fun findById(id: Long): Course? =
		jpaRepository.findById(id).orElse(null)?.toDomain()

	override fun findAllByStatus(status: CourseStatus, pageRequest: DomainPageRequest): PageResult<Course> {
		val page = jpaRepository.findAllByStatus(status, SpringPageRequest.of(pageRequest.page, pageRequest.size))
		return PageResult(
			items = page.content.map { it.toDomain() },
			page = pageRequest.page,
			size = pageRequest.size,
			totalCount = page.totalElements,
		)
	}

	private fun Course.toEntity(): CourseJpaEntity = CourseJpaEntity(
		id = id,
		title = title,
		description = description,
		price = price,
		instructorId = instructorId.value,
		status = status,
	)

	private fun CourseJpaEntity.toDomain(): Course = Course.reconstitute(
		id = requireNotNull(id) { "persisted CourseJpaEntity must have an id" },
		title = title,
		description = description,
		price = price,
		instructorId = UserId(instructorId),
		status = status,
	)
}
