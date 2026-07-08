package com.classplatform.enrollment

import com.classplatform.common.UserId
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseRepository
import com.classplatform.enrollment.application.EnrollUseCase
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.exception.DuplicateEnrollmentException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@Testcontainers
@SpringBootTest
class EnrollmentConcurrencyIT {

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
	private lateinit var enrollUseCase: EnrollUseCase

	@Autowired
	private lateinit var courseRepository: CourseRepository

	@Autowired
	private lateinit var enrollmentRepository: EnrollmentRepository

	@Test
	fun `동일 사용자·강의에 100개 동시 요청이 와도 신청은 정확히 1건만 생성된다`() {
		val course = Course.register("동시성 테스트 강의", null, BigDecimal.ZERO, UserId(1L))
		course.publish()
		val courseId = requireNotNull(courseRepository.save(course).id)
		val userId = UserId(2L)

		val threadCount = 100
		val executor = Executors.newFixedThreadPool(threadCount)
		val readyLatch = CountDownLatch(threadCount)
		val startLatch = CountDownLatch(1)
		val doneLatch = CountDownLatch(threadCount)
		val unexpectedErrors = CopyOnWriteArrayList<Throwable>()

		repeat(threadCount) {
			executor.submit {
				readyLatch.countDown()
				startLatch.await()
				try {
					enrollUseCase.execute(courseId, userId)
				} catch (ex: DuplicateEnrollmentException) {
					// 기대된 결과 — 이미 신청된 사용자의 나머지 동시 요청은 전부 거부되어야 한다
				} catch (ex: Throwable) {
					unexpectedErrors.add(ex)
				} finally {
					doneLatch.countDown()
				}
			}
		}

		readyLatch.await(10, TimeUnit.SECONDS)
		startLatch.countDown()
		doneLatch.await(60, TimeUnit.SECONDS)
		executor.shutdown()

		assertEquals(emptyList<Throwable>(), unexpectedErrors)
		val enrollments = enrollmentRepository.findAllByUserId(userId)
		assertEquals(1, enrollments.size)
	}
}
