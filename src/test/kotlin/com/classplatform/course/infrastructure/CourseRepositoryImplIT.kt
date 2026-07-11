package com.classplatform.course.infrastructure

import com.classplatform.common.PageRequest
import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.domain.CourseStatus
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CourseRepositoryImpl::class)
class CourseRepositoryImplIT {

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
	private lateinit var courseRepository: CourseRepository

	@Test
	fun `저장 후 조회하면 동일한 강의 정보를 반환한다`() {
		val course = Course.register(
			title = "스프링 입문",
			description = "스프링 부트 기초",
			price = BigDecimal("10000.00"),
			instructorId = UserId(1L),
		)

		val saved = courseRepository.save(course)

		assertNotNull(saved.id)
		val found = courseRepository.findById(saved.id!!)
		assertNotNull(found)
		assertEquals("스프링 입문", found.title)
		assertEquals(CourseStatus.DRAFT, found.status)
	}

	@Test
	fun `PUBLISHED 상태의 강의만 목록에서 조회된다`() {
		courseRepository.save(Course.register("초안 강의", null, BigDecimal.ZERO, UserId(1L)))
		val published = Course.register("공개 강의", null, BigDecimal.ZERO, UserId(1L))
		published.publish()
		courseRepository.save(published)

		val result = courseRepository.findAllByStatus(CourseStatus.PUBLISHED, PageRequest(page = 0, size = 10))

		assertEquals(1, result.items.size)
		assertEquals("공개 강의", result.items.first().title)
	}

	@Test
	fun `강사별 강의 목록은 상태 무관하게 조회된다`() {
		courseRepository.save(Course.register("초안 강의", null, BigDecimal.ZERO, UserId(1L)))
		val published = Course.register("공개 강의", null, BigDecimal.ZERO, UserId(1L))
		published.publish()
		courseRepository.save(published)
		courseRepository.save(Course.register("타 강사 강의", null, BigDecimal.ZERO, UserId(2L)))

		val result = courseRepository.findAllByInstructorId(UserId(1L))

		assertEquals(2, result.size)
		assertEquals(setOf("초안 강의", "공개 강의"), result.map { it.title }.toSet())
	}
}
