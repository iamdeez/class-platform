package com.classplatform.course.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.course.domain.exception.CourseNotFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class GetCourseUseCaseTest {

	private val courseRepository = mockk<CourseRepository>()
	private val useCase = GetCourseUseCase(courseRepository)

	@Test
	fun `존재하는 강의를 조회하면 반환한다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(1L), CourseStatus.DRAFT)
		every { courseRepository.findById(1L) } returns course

		val result = useCase.execute(1L)

		assertEquals("제목", result.title)
	}

	@Test
	fun `존재하지 않는 강의를 조회하면 예외가 발생한다`() {
		every { courseRepository.findById(999L) } returns null

		assertThrows<CourseNotFoundException> { useCase.execute(999L) }
	}
}
