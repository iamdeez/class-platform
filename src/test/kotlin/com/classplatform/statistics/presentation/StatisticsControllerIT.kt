package com.classplatform.statistics.presentation

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import kotlin.test.assertEquals

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatisticsControllerIT {

	companion object {
		@Container
		@JvmStatic
		val mysql = MySQLContainer("mysql:8.0")

		@JvmStatic
		@DynamicPropertySource
		fun overrideProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url", mysql::getJdbcUrl)
			registry.add("spring.datasource.username", mysql::getUsername)
			registry.add("spring.datasource.password", mysql::getPassword)
		}
	}

	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var courseRepository: CourseRepository

	@Autowired
	private lateinit var enrollmentRepository: EnrollmentRepository

	private val instructorId = UserId(100L)
	private val coursePrice = BigDecimal("10000.00")

	private fun createCourse(title: String, instructor: UserId = instructorId): Course =
		courseRepository.save(Course.register(title, null, coursePrice, instructor))

	private fun enroll(courseId: Long, userId: Long): Enrollment =
		enrollmentRepository.save(Enrollment.create(courseId, UserId(userId), coursePrice))

	@Test
	fun `SC-001 강사가 자신의 강의 목록 통계를 조회하면 각 강의마다 집계 값이 함께 반환된다`() {
		val courseA = createCourse("강의A")
		enroll(requireNotNull(courseA.id), 1L)
		enroll(requireNotNull(courseA.id), 2L)
		createCourse("강의B")

		val response = mockMvc.perform(get("/api/instructors/me/statistics").header("X-User-Id", "100"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andReturn()
			.response
			.getContentAsString(Charsets.UTF_8)
		val items = ObjectMapper().readTree(response)["data"]["items"]
		val byTitle = items.associateBy { it["title"].asText() }

		assertEquals(2, byTitle.getValue("강의A")["studentCount"].asInt())
		assertEquals(20000.00, byTitle.getValue("강의A")["revenue"].asDouble())
		assertEquals(0, byTitle.getValue("강의B")["studentCount"].asInt())
	}

	@Test
	fun `SC-002 강사가 특정 강의의 상세 통계를 조회하면 정확한 집계 값이 반환된다`() {
		val course = createCourse("강의C")
		val courseId = requireNotNull(course.id)
		enroll(courseId, 1L)
		val completed = enroll(courseId, 2L)
		completed.complete()
		enrollmentRepository.save(completed)
		val cancelled = enroll(courseId, 3L)
		cancelled.cancel()
		enrollmentRepository.save(cancelled)

		mockMvc.perform(get("/api/courses/$courseId/statistics").header("X-User-Id", "100"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.studentCount").value(2))
			.andExpect(jsonPath("$.data.revenue").value(20000.00))
			.andExpect(jsonPath("$.data.completionRate").value(0.5))
			.andExpect(jsonPath("$.data.cancellationRate").value(1.0 / 3))
	}

	@Test
	fun `SC-003 담당 강사가 아닌 사용자가 강의 통계를 조회하면 요청이 거부된다`() {
		val course = createCourse("강의D")

		mockMvc.perform(get("/api/courses/${course.id}/statistics").header("X-User-Id", "999"))
			.andExpect(status().isForbidden)
	}
}
