package com.classplatform.statistics.infrastructure

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.statistics.domain.CourseStatisticsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Testcontainers
@SpringBootTest
@Transactional
class CourseStatisticsRepositoryImplIT {

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
	private lateinit var courseStatisticsRepository: CourseStatisticsRepository

	@Autowired
	private lateinit var courseRepository: CourseRepository

	@Autowired
	private lateinit var enrollmentRepository: EnrollmentRepository

	private val instructorId = UserId(100L)
	private val coursePrice = BigDecimal("10000.00")

	private fun createCourse(title: String): Course =
		courseRepository.save(Course.register(title, null, coursePrice, instructorId))

	private fun enroll(courseId: Long, userId: Long, price: BigDecimal = coursePrice): Enrollment =
		enrollmentRepository.save(Enrollment.create(courseId, UserId(userId), price))

	@Test
	fun `ACTIVE COMPLETED CANCELLED가 혼합된 강의의 통계가 정확히 집계된다`() {
		val course = createCourse("혼합 상태 강의")
		val courseId = requireNotNull(course.id)

		enroll(courseId, 1L)
		enroll(courseId, 2L)
		val completed = enroll(courseId, 3L)
		completed.complete()
		enrollmentRepository.save(completed)
		val cancelled = enroll(courseId, 4L)
		cancelled.cancel()
		enrollmentRepository.save(cancelled)

		val statistics = requireNotNull(courseStatisticsRepository.findByCourseId(courseId))

		assertEquals(3L, statistics.studentCount)
		assertEquals(0, BigDecimal("30000.00").compareTo(statistics.revenue))
		assertEquals(1.0 / 3, statistics.completionRate)
		assertEquals(1.0 / 4, statistics.cancellationRate)
	}

	@Test
	fun `수강생이 없는 강의는 0으로 나누기 대신 0을 반환한다`() {
		val course = createCourse("수강생 없는 강의")
		val courseId = requireNotNull(course.id)

		val statistics = requireNotNull(courseStatisticsRepository.findByCourseId(courseId))

		assertEquals(0L, statistics.studentCount)
		assertEquals(0, BigDecimal.ZERO.compareTo(statistics.revenue))
		assertEquals(0.0, statistics.completionRate)
		assertEquals(0.0, statistics.cancellationRate)
	}

	@Test
	fun `강사별 강의 목록 통계는 해당 강사의 강의만 포함한다`() {
		val course = createCourse("내 강의")
		enroll(requireNotNull(course.id), 1L)
		courseRepository.save(Course.register("타 강사 강의", null, coursePrice, UserId(999L)))

		val results = courseStatisticsRepository.findAllByInstructorId(instructorId)

		assertEquals(setOf("내 강의"), results.map { it.title }.toSet())
	}

	@Test
	fun `존재하지 않는 강의를 조회하면 null을 반환한다`() {
		assertNull(courseStatisticsRepository.findByCourseId(999_999L))
	}

	@Test
	fun `SC-007 강의 가격이 변경된 후에도 매출은 신청 시점 가격 기준으로 유지된다`() {
		val course = createCourse("가격 변경 강의")
		val courseId = requireNotNull(course.id)
		enroll(courseId, 1L, price = BigDecimal("10000.00"))
		enroll(courseId, 2L, price = BigDecimal("15000.00"))

		val statistics = requireNotNull(courseStatisticsRepository.findByCourseId(courseId))

		// course.price(현재값)와 무관하게 각 Enrollment의 price 스냅샷 합(10000+15000)이 매출이다.
		assertEquals(0, BigDecimal("25000.00").compareTo(statistics.revenue))
	}

	@Test
	fun `SC-008 취소된 수강은 매출 집계에서 제외된다`() {
		val course = createCourse("취소 포함 강의")
		val courseId = requireNotNull(course.id)
		enroll(courseId, 1L)
		val cancelled = enroll(courseId, 2L)
		cancelled.cancel()
		enrollmentRepository.save(cancelled)

		val statistics = requireNotNull(courseStatisticsRepository.findByCourseId(courseId))

		assertEquals(0, coursePrice.compareTo(statistics.revenue))
	}
}
