package com.classplatform.course.presentation

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
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

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseControllerIT {

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

	@Test
	fun `목록 조회는 PUBLISHED 상태의 강의만 반환한다`() {
		courseRepository.save(Course.register("초안", null, BigDecimal.ZERO, UserId(1L)))

		val published1 = Course.register("공개1", null, BigDecimal.ZERO, UserId(1L))
		published1.publish()
		courseRepository.save(published1)

		val published2 = Course.register("공개2", null, BigDecimal.ZERO, UserId(1L))
		published2.publish()
		courseRepository.save(published2)

		val closed = Course.register("종료", null, BigDecimal.ZERO, UserId(1L))
		closed.publish()
		closed.close()
		courseRepository.save(closed)

		mockMvc.perform(get("/api/courses"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.totalCount").value(2))
	}
}
