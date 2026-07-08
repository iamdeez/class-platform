package com.classplatform.enrollment.infrastructure

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.course.infrastructure.CourseRepositoryImpl
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.exception.DuplicateEnrollmentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
@Import(EnrollmentRepositoryImpl::class, CourseRepositoryImpl::class)
class EnrollmentRepositoryImplIT {

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
	private lateinit var enrollmentRepository: EnrollmentRepository

	@Autowired
	private lateinit var courseRepository: CourseRepository

	private fun createCourse(): Course =
		courseRepository.save(Course.register("강의", null, BigDecimal.ZERO, UserId(1L)))

	@Test
	fun `저장 후 조회하면 동일한 신청 정보를 반환한다`() {
		val course = createCourse()
		val enrollment = Enrollment.create(courseId = requireNotNull(course.id), userId = UserId(1L))

		val saved = enrollmentRepository.save(enrollment)

		assertNotNull(saved.id)
		val found = enrollmentRepository.findById(saved.id!!)
		assertNotNull(found)
		assertEquals(course.id, found.courseId)
	}

	@Test
	fun `동일 사용자가 동일 강의에 중복 신청하면 예외가 발생한다`() {
		val courseId = requireNotNull(createCourse().id)
		enrollmentRepository.save(Enrollment.create(courseId = courseId, userId = UserId(1L)))

		assertThrows<DuplicateEnrollmentException> {
			enrollmentRepository.save(Enrollment.create(courseId = courseId, userId = UserId(1L)))
		}
	}
}
