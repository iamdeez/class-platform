package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.EnrollmentStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class ListMyEnrollmentsUseCaseTest {

	private val enrollmentRepository = mockk<EnrollmentRepository>()
	private val useCase = ListMyEnrollmentsUseCase(enrollmentRepository)

	@Test
	fun `취소된 신청은 목록에서 제외된다`() {
		val userId = UserId(2L)
		val active = Enrollment.reconstitute(1L, 10L, userId, BigDecimal.ZERO, EnrollmentStatus.ACTIVE)
		val cancelled = Enrollment.reconstitute(2L, 11L, userId, BigDecimal.ZERO, EnrollmentStatus.CANCELLED)
		every { enrollmentRepository.findAllByUserId(userId) } returns listOf(active, cancelled)

		val result = useCase.execute(userId)

		assertEquals(listOf(active), result)
	}

	@Test
	fun `완료된 신청은 목록에 포함된다`() {
		val userId = UserId(2L)
		val active = Enrollment.reconstitute(1L, 10L, userId, BigDecimal.ZERO, EnrollmentStatus.ACTIVE)
		val completed = Enrollment.reconstitute(2L, 11L, userId, BigDecimal.ZERO, EnrollmentStatus.COMPLETED)
		every { enrollmentRepository.findAllByUserId(userId) } returns listOf(active, completed)

		val result = useCase.execute(userId)

		assertEquals(listOf(active, completed), result)
	}
}
