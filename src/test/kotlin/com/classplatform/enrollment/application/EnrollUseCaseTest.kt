package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.course.domain.exception.CourseNotEnrollableException
import com.classplatform.course.domain.exception.CourseNotFoundException
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class EnrollUseCaseTest {

	private val enrollmentRepository = mockk<EnrollmentRepository>()
	private val courseRepository = mockk<CourseRepository>()
	private val useCase = EnrollUseCase(enrollmentRepository, courseRepository)

	@Test
	fun `PUBLISHED 강의에 신청하면 수강신청이 생성된다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		every { courseRepository.findById(1L) } returns course
		val savedSlot = slot<Enrollment>()
		every { enrollmentRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute(courseId = 1L, userId = UserId(2L))

		assertEquals(1L, result.courseId)
	}

	@Test
	fun `DRAFT 강의에 신청하면 예외가 발생한다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.DRAFT)
		every { courseRepository.findById(1L) } returns course

		assertThrows<CourseNotEnrollableException> { useCase.execute(courseId = 1L, userId = UserId(2L)) }
	}

	@Test
	fun `CLOSED 강의에 신청하면 예외가 발생한다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.CLOSED)
		every { courseRepository.findById(1L) } returns course

		assertThrows<CourseNotEnrollableException> { useCase.execute(courseId = 1L, userId = UserId(2L)) }
	}

	@Test
	fun `존재하지 않는 강의에 신청하면 예외가 발생한다`() {
		every { courseRepository.findById(1L) } returns null

		assertThrows<CourseNotFoundException> { useCase.execute(courseId = 1L, userId = UserId(2L)) }
	}
}
