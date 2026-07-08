package com.classplatform.course.domain

import com.classplatform.common.PageRequest
import com.classplatform.common.PageResult

interface CourseRepository {
	fun save(course: Course): Course

	fun findById(id: Long): Course?

	fun findAllByStatus(status: CourseStatus, pageRequest: PageRequest): PageResult<Course>
}
