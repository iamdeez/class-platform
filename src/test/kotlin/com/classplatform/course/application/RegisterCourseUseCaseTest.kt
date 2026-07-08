package com.classplatform.course.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class RegisterCourseUseCaseTest {

	private val courseRepository = mockk<CourseRepository>()
	private val useCase = RegisterCourseUseCase(courseRepository)

	@Test
	fun `강의를 등록하면 저장소에 저장된 강의를 반환한다`() {
		val savedSlot = slot<Course>()
		every { courseRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("코틀린 입문", "설명", BigDecimal("5000"), UserId(1L))

		assertEquals("코틀린 입문", result.title)
		verify(exactly = 1) { courseRepository.save(any()) }
	}
}
