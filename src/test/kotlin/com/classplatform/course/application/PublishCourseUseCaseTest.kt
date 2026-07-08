package com.classplatform.course.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.course.domain.exception.CourseNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class PublishCourseUseCaseTest {

	private val courseRepository = mockk<CourseRepository>()
	private val useCase = PublishCourseUseCase(courseRepository)

	@Test
	fun `DRAFT 강의를 공개 상태로 전이한다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(1L), CourseStatus.DRAFT)
		every { courseRepository.findById(1L) } returns course
		val savedSlot = slot<Course>()
		every { courseRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute(1L)

		assertEquals(CourseStatus.PUBLISHED, result.status)
	}

	@Test
	fun `존재하지 않는 강의면 예외가 발생한다`() {
		every { courseRepository.findById(1L) } returns null

		assertThrows<CourseNotFoundException> { useCase.execute(1L) }
	}
}
