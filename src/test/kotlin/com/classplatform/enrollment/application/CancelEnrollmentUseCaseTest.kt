package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.EnrollmentStatus
import com.classplatform.enrollment.domain.exception.EnrollmentAccessDeniedException
import com.classplatform.enrollment.domain.exception.EnrollmentCancellationNotAllowedException
import com.classplatform.enrollment.domain.exception.EnrollmentNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class CancelEnrollmentUseCaseTest {

	private val enrollmentRepository = mockk<EnrollmentRepository>()
	private val courseRepository = mockk<CourseRepository>()
	private val useCase = CancelEnrollmentUseCase(enrollmentRepository, courseRepository)

	@Test
	fun `본인 신청을 취소하면 상태가 CANCELLED로 바뀐다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), EnrollmentStatus.ACTIVE)
		val course = Course.reconstitute(10L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		every { enrollmentRepository.findById(1L) } returns enrollment
		every { courseRepository.findById(10L) } returns course
		val savedSlot = slot<Enrollment>()
		every { enrollmentRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute(enrollmentId = 1L, requesterId = UserId(2L))

		assertEquals(EnrollmentStatus.CANCELLED, result.status)
	}

	@Test
	fun `타인의 신청을 취소하려 하면 예외가 발생한다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), EnrollmentStatus.ACTIVE)
		every { enrollmentRepository.findById(1L) } returns enrollment

		assertThrows<EnrollmentAccessDeniedException> {
			useCase.execute(enrollmentId = 1L, requesterId = UserId(3L))
		}
	}

	@Test
	fun `CLOSED 강의의 신청은 취소할 수 없다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), EnrollmentStatus.ACTIVE)
		val course = Course.reconstitute(10L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.CLOSED)
		every { enrollmentRepository.findById(1L) } returns enrollment
		every { courseRepository.findById(10L) } returns course

		assertThrows<EnrollmentCancellationNotAllowedException> {
			useCase.execute(enrollmentId = 1L, requesterId = UserId(2L))
		}
	}

	@Test
	fun `존재하지 않는 신청을 취소하려 하면 예외가 발생한다`() {
		every { enrollmentRepository.findById(1L) } returns null

		assertThrows<EnrollmentNotFoundException> {
			useCase.execute(enrollmentId = 1L, requesterId = UserId(2L))
		}
	}
}
