package com.classplatform.statistics.infrastructure

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class CourseStatisticsRepositoryImplTest {

	private val mapper = mockk<CourseStatisticsMapper>()
	private val repository = CourseStatisticsRepositoryImpl(mapper)

	@Test
	fun `SC-009 완료율은 완료 수를 활성+완료 수로 나눈 값이다`() {
		val row = CourseStatisticsRow(
			courseId = 1L,
			title = "강의",
			studentCount = 5L,
			revenue = BigDecimal("50000.00"),
			completedCount = 2L,
			cancelledCount = 1L,
			totalCount = 6L,
		)
		every { mapper.findByCourseId(1L) } returns row

		val result = requireNotNull(repository.findByCourseId(1L))

		assertEquals(2.0 / 5, result.completionRate)
	}

	@Test
	fun `SC-009 활성+완료 수강생이 없으면 완료율은 0이다`() {
		val row = CourseStatisticsRow(
			courseId = 1L,
			title = "강의",
			studentCount = 0L,
			revenue = BigDecimal.ZERO,
			completedCount = 0L,
			cancelledCount = 0L,
			totalCount = 0L,
		)
		every { mapper.findByCourseId(1L) } returns row

		val result = requireNotNull(repository.findByCourseId(1L))

		assertEquals(0.0, result.completionRate)
	}

	@Test
	fun `SC-010 취소율은 취소 수를 전체 신청 수로 나눈 값이다`() {
		val row = CourseStatisticsRow(
			courseId = 1L,
			title = "강의",
			studentCount = 5L,
			revenue = BigDecimal("50000.00"),
			completedCount = 2L,
			cancelledCount = 1L,
			totalCount = 6L,
		)
		every { mapper.findByCourseId(1L) } returns row

		val result = requireNotNull(repository.findByCourseId(1L))

		assertEquals(1.0 / 6, result.cancellationRate)
	}

	@Test
	fun `SC-010 전체 신청이 없으면 취소율은 0이다`() {
		val row = CourseStatisticsRow(
			courseId = 1L,
			title = "강의",
			studentCount = 0L,
			revenue = BigDecimal.ZERO,
			completedCount = 0L,
			cancelledCount = 0L,
			totalCount = 0L,
		)
		every { mapper.findByCourseId(1L) } returns row

		val result = requireNotNull(repository.findByCourseId(1L))

		assertEquals(0.0, result.cancellationRate)
	}
}
