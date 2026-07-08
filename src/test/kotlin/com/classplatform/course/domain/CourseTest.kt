package com.classplatform.course.domain

import com.classplatform.common.UserId
import com.classplatform.course.domain.exception.InvalidCourseStatusTransitionException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class CourseTest {

	@Test
	fun `제목이 비어있으면 생성이 거부된다`() {
		assertThrows<IllegalArgumentException> {
			Course.register(title = "", description = null, price = BigDecimal.ZERO, instructorId = UserId(1L))
		}
	}

	@Test
	fun `가격이 음수이면 생성이 거부된다`() {
		assertThrows<IllegalArgumentException> {
			Course.register(
				title = "제목",
				description = null,
				price = BigDecimal("-1"),
				instructorId = UserId(1L),
			)
		}
	}

	@Test
	fun `정상 생성된 강의의 초기 상태는 DRAFT이다`() {
		val course = Course.register(
			title = "제목",
			description = null,
			price = BigDecimal.ZERO,
			instructorId = UserId(1L),
		)

		assertEquals(CourseStatus.DRAFT, course.status)
	}

	@Test
	fun `DRAFT 상태에서 close를 직접 호출하면 예외가 발생한다`() {
		val course = Course.register(
			title = "제목",
			description = null,
			price = BigDecimal.ZERO,
			instructorId = UserId(1L),
		)

		assertThrows<InvalidCourseStatusTransitionException> { course.close() }
	}
}
