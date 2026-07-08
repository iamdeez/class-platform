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
class EnrollmentControllerIT {

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

	private fun publishedCourseId(): Long {
		val course = Course.register("강의", null, BigDecimal.ZERO, UserId(1L))
		course.publish()
		return requireNotNull(courseRepository.save(course).id)
	}

	@Test
	fun `동일 사용자가 동일 강의에 두 번째 신청을 하면 409를 반환한다`() {
		val courseId = publishedCourseId()

		mockMvc.perform(post("/api/courses/$courseId/enrollments").header("X-User-Id", "2"))
			.andExpect(status().isCreated)

		mockMvc.perform(post("/api/courses/$courseId/enrollments").header("X-User-Id", "2"))
			.andExpect(status().isConflict)
	}

	@Test
	fun `수강 신청을 취소하면 내 신청 목록에서 제외된다`() {
		val courseId = publishedCourseId()

		val response = mockMvc.perform(post("/api/courses/$courseId/enrollments").header("X-User-Id", "3"))
			.andExpect(status().isCreated)
			.andReturn()
			.response
			.contentAsString
		val enrollmentId = ObjectMapper().readTree(response)["data"]["enrollmentId"].asLong()

		mockMvc.perform(get("/api/users/me/enrollments").header("X-User-Id", "3"))
			.andExpect(jsonPath("$.data.items.length()").value(1))

		mockMvc.perform(delete("/api/enrollments/$enrollmentId").header("X-User-Id", "3"))
			.andExpect(status().isNoContent)

		mockMvc.perform(get("/api/users/me/enrollments").header("X-User-Id", "3"))
			.andExpect(jsonPath("$.data.items.length()").value(0))
	}
}
