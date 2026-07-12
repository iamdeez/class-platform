package com.classplatform.statistics.application

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import com.classplatform.course.domain.exception.CourseAccessDeniedException
import com.classplatform.course.domain.exception.CourseNotFoundException
import com.classplatform.statistics.domain.CourseStatistics
import com.classplatform.statistics.domain.CourseStatisticsRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class GetCourseStatisticsUseCaseTest {

	private val courseRepository = mockk<CourseRepository>()
	private val courseStatisticsRepository = mockk<CourseStatisticsRepository>()
	private val useCase = GetCourseStatisticsUseCase(courseRepository, courseStatisticsRepository)

	@Test
	fun `담당 강사가 조회하면 강의 통계를 반환한다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		val statistics = CourseStatistics(1L, "제목", 3L, BigDecimal("30000.00"), 1.0 / 3, 0.0)
		every { courseRepository.findById(1L) } returns course
		every { courseStatisticsRepository.findByCourseId(1L) } returns statistics

		val result = useCase.execute(courseId = 1L, requesterId = UserId(9L))

		assertEquals(statistics, result)
	}

	@Test
	fun `존재하지 않는 강의를 조회하면 예외가 발생한다`() {
		every { courseRepository.findById(1L) } returns null

		assertThrows<CourseNotFoundException> {
			useCase.execute(courseId = 1L, requesterId = UserId(9L))
		}
	}

	@Test
	fun `담당 강사가 아니면 예외가 발생한다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(9L), CourseStatus.PUBLISHED)
		every { courseRepository.findById(1L) } returns course

		assertThrows<CourseAccessDeniedException> {
			useCase.execute(courseId = 1L, requesterId = UserId(3L))
		}
	}
}
