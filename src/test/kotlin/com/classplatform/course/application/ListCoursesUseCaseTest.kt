package com.classplatform.course.application

import com.classplatform.common.PageRequest
import com.classplatform.common.PageResult
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ListCoursesUseCaseTest {

	private val courseRepository = mockk<CourseRepository>()
	private val useCase = ListCoursesUseCase(courseRepository)

	@Test
	fun `PUBLISHED 상태로 목록 조회를 위임한다`() {
		val pageRequest = PageRequest(0, 10)
		every { courseRepository.findAllByStatus(CourseStatus.PUBLISHED, pageRequest) } returns
			PageResult(emptyList(), 0, 10, 0)

		useCase.execute(pageRequest)

		verify(exactly = 1) { courseRepository.findAllByStatus(CourseStatus.PUBLISHED, pageRequest) }
	}
}
