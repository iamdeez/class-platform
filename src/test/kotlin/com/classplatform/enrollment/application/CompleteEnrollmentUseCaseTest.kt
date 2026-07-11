package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.EnrollmentStatus
import com.classplatform.enrollment.domain.exception.EnrollmentAccessDeniedException
import com.classplatform.enrollment.domain.exception.EnrollmentNotFoundException
import com.classplatform.enrollment.domain.exception.InvalidEnrollmentStatusException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class CompleteEnrollmentUseCaseTest {

	private val enrollmentRepository = mockk<EnrollmentRepository>()
	private val courseRepository = mockk<CourseRepository>()
	private val useCase = CompleteEnrollmentUseCase(enrollmentRepository, courseRepository)

	@Test
	fun `담당 강사가 완료 처리하면 상태가 COMPLETED로 바뀐다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), BigDecimal.ZERO, EnrollmentStatus.ACTIVE)
		val course = Course.reconstitute(10L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		every { enrollmentRepository.findById(1L) } returns enrollment
		every { courseRepository.findById(10L) } returns course
		val savedSlot = slot<Enrollment>()
		every { enrollmentRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute(enrollmentId = 1L, requesterId = UserId(9L))

		assertEquals(EnrollmentStatus.COMPLETED, result.status)
	}

	@Test
	fun `존재하지 않는 신청을 완료 처리하려 하면 예외가 발생한다`() {
		every { enrollmentRepository.findById(1L) } returns null

		assertThrows<EnrollmentNotFoundException> {
			useCase.execute(enrollmentId = 1L, requesterId = UserId(9L))
		}
	}

	@Test
	fun `담당 강사가 아니면 완료 처리 시 예외가 발생한다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), BigDecimal.ZERO, EnrollmentStatus.ACTIVE)
		val course = Course.reconstitute(10L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		every { enrollmentRepository.findById(1L) } returns enrollment
		every { courseRepository.findById(10L) } returns course

		assertThrows<EnrollmentAccessDeniedException> {
			useCase.execute(enrollmentId = 1L, requesterId = UserId(3L))
		}
	}

	@Test
	fun `이미 취소된 신청을 완료 처리하려 하면 예외가 발생한다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), BigDecimal.ZERO, EnrollmentStatus.CANCELLED)
		val course = Course.reconstitute(10L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		every { enrollmentRepository.findById(1L) } returns enrollment
		every { courseRepository.findById(10L) } returns course

		assertThrows<InvalidEnrollmentStatusException> {
			useCase.execute(enrollmentId = 1L, requesterId = UserId(9L))
		}
	}

	@Test
	fun `이미 완료된 신청을 다시 완료 처리하려 하면 예외가 발생한다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), BigDecimal.ZERO, EnrollmentStatus.COMPLETED)
		val course = Course.reconstitute(10L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		every { enrollmentRepository.findById(1L) } returns enrollment
		every { courseRepository.findById(10L) } returns course

		assertThrows<InvalidEnrollmentStatusException> {
			useCase.execute(enrollmentId = 1L, requesterId = UserId(9L))
		}
	}
}
