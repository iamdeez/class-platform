package com.classplatform.statistics.application

import com.classplatform.common.UserId
import com.classplatform.statistics.domain.CourseStatistics
import com.classplatform.statistics.domain.CourseStatisticsRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class GetInstructorStatisticsUseCaseTest {

	private val courseStatisticsRepository = mockk<CourseStatisticsRepository>()
	private val useCase = GetInstructorStatisticsUseCase(courseStatisticsRepository)

	@Test
	fun `강사의 강의별 통계 목록을 반환한다`() {
		val instructorId = UserId(1L)
		val statistics = listOf(
			CourseStatistics(1L, "강의A", 3L, BigDecimal("30000.00"), 1.0 / 3, 0.0),
			CourseStatistics(2L, "강의B", 0L, BigDecimal.ZERO, 0.0, 0.0),
		)
		every { courseStatisticsRepository.findAllByInstructorId(instructorId) } returns statistics

		val result = useCase.execute(instructorId)

		assertEquals(statistics, result)
	}
}
