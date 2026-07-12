package com.classplatform.statistics.presentation

import com.classplatform.common.UserId
import com.classplatform.course.domain.exception.CourseAccessDeniedException
import com.classplatform.statistics.application.GetCourseStatisticsUseCase
import com.classplatform.statistics.application.GetInstructorStatisticsUseCase
import com.classplatform.statistics.domain.CourseStatistics
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@WebMvcTest(StatisticsController::class)
class StatisticsControllerTest {

	@Autowired
	private lateinit var mockMvc: MockMvc

	@MockkBean
	private lateinit var getInstructorStatisticsUseCase: GetInstructorStatisticsUseCase

	@MockkBean
	private lateinit var getCourseStatisticsUseCase: GetCourseStatisticsUseCase

	@Test
	fun `강사 통계 목록 조회는 200과 items 배열을 반환한다`() {
		val statistics = listOf(
			CourseStatistics(1L, "강의A", 3L, BigDecimal("30000.00"), 1.0 / 3, 0.0),
		)
		every { getInstructorStatisticsUseCase.execute(UserId(9L)) } returns statistics

		mockMvc.perform(get("/api/instructors/me/statistics").header("X-User-Id", "9"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items[0].courseId").value(1))
			.andExpect(jsonPath("$.data.items[0].title").value("강의A"))
			.andExpect(jsonPath("$.data.items[0].studentCount").value(3))
	}

	@Test
	fun `단일 강의 통계 조회는 200과 통계 필드를 반환한다`() {
		val statistics = CourseStatistics(1L, "강의A", 3L, BigDecimal("30000.00"), 1.0 / 3, 0.0)
		every { getCourseStatisticsUseCase.execute(1L, UserId(9L)) } returns statistics

		mockMvc.perform(get("/api/courses/1/statistics").header("X-User-Id", "9"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.courseId").value(1))
			.andExpect(jsonPath("$.data.revenue").value(30000.00))
			.andExpect(jsonPath("$.data.completionRate").value(1.0 / 3))
	}

	@Test
	fun `담당 강사가 아니면 403을 반환한다`() {
		every { getCourseStatisticsUseCase.execute(1L, UserId(3L)) } throws
			CourseAccessDeniedException("forbidden")

		mockMvc.perform(get("/api/courses/1/statistics").header("X-User-Id", "3"))
			.andExpect(status().isForbidden)
	}
}
