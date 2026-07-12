package com.classplatform.enrollment.presentation

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnrollmentCompletionIT {

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

	private val instructorId = 9L

	private fun publishedCourseId(): Long {
		val course = Course.register("강의", null, BigDecimal.ZERO, UserId(instructorId))
		course.publish()
		return requireNotNull(courseRepository.save(course).id)
	}

	private fun enroll(courseId: Long, studentId: Long): Long {
		val response = mockMvc.perform(post("/api/courses/$courseId/enrollments").header("X-User-Id", studentId))
			.andExpect(status().isCreated)
			.andReturn()
			.response
			.getContentAsString(Charsets.UTF_8)
		return ObjectMapper().readTree(response)["data"]["enrollmentId"].asLong()
	}

	@Test
	fun `SC-004 강사가 완료 처리하면 이후 조회에서 완료 상태로 표시된다`() {
		val courseId = publishedCourseId()
		val enrollmentId = enroll(courseId, 1L)

		mockMvc.perform(post("/api/enrollments/$enrollmentId/complete").header("X-User-Id", instructorId))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.status").value("COMPLETED"))

		mockMvc.perform(get("/api/users/me/enrollments").header("X-User-Id", "1"))
			.andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
	}

	@Test
	fun `SC-005 담당 강사가 아닌 사용자가 완료 처리를 요청하면 거부된다`() {
		val courseId = publishedCourseId()
		val enrollmentId = enroll(courseId, 1L)

		mockMvc.perform(post("/api/enrollments/$enrollmentId/complete").header("X-User-Id", "999"))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `SC-006 이미 취소된 수강을 다시 완료 처리하려 하면 거부된다`() {
		val courseId = publishedCourseId()
		val enrollmentId = enroll(courseId, 1L)
		mockMvc.perform(delete("/api/enrollments/$enrollmentId").header("X-User-Id", "1"))
			.andExpect(status().isNoContent)

		mockMvc.perform(post("/api/enrollments/$enrollmentId/complete").header("X-User-Id", instructorId))
			.andExpect(status().isConflict)
	}

	@Test
	fun `SC-006 이미 완료된 수강을 다시 완료 처리하려 하면 거부된다`() {
		val courseId = publishedCourseId()
		val enrollmentId = enroll(courseId, 1L)
		mockMvc.perform(post("/api/enrollments/$enrollmentId/complete").header("X-User-Id", instructorId))
			.andExpect(status().isOk)

		mockMvc.perform(post("/api/enrollments/$enrollmentId/complete").header("X-User-Id", instructorId))
			.andExpect(status().isConflict)
	}
}
