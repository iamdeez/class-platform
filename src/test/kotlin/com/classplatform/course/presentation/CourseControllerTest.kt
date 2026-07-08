package com.classplatform.course.presentation

import com.classplatform.common.PageResult
import com.classplatform.common.UserId
import com.classplatform.course.application.CloseCourseUseCase
import com.classplatform.course.application.GetCourseUseCase
import com.classplatform.course.application.ListCoursesUseCase
import com.classplatform.course.application.PublishCourseUseCase
import com.classplatform.course.application.RegisterCourseUseCase
import com.classplatform.course.domain.Course
import com.classplatform.course.domain.CourseStatus
import com.classplatform.course.domain.exception.CourseNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@WebMvcTest(CourseController::class)
class CourseControllerTest {

	@Autowired
	private lateinit var mockMvc: MockMvc

	@MockkBean
	private lateinit var registerCourseUseCase: RegisterCourseUseCase

	@MockkBean
	private lateinit var getCourseUseCase: GetCourseUseCase

	@MockkBean
	private lateinit var listCoursesUseCase: ListCoursesUseCase

	@MockkBean
	private lateinit var publishCourseUseCase: PublishCourseUseCase

	@MockkBean
	private lateinit var closeCourseUseCase: CloseCourseUseCase

	@Test
	fun `강의 등록 요청이 성공하면 201과 courseId를 반환한다`() {
		val course = Course.reconstitute(1L, "제목", null, BigDecimal.ZERO, UserId(1L), CourseStatus.DRAFT)
		// UserId는 @JvmInline value class라 MockK의 any()가 내부적으로 임의 값을 생성해 넣는데,
		// 그 값이 음수로 나오면 UserId의 유효성 검증(require)에 걸려 테스트가 무작위로 실패한다.
		// 그래서 이 파라미터만 명시적인 값으로 매칭한다.
		every { registerCourseUseCase.execute(any(), any(), any(), UserId(1L)) } returns course

		mockMvc.perform(
			post("/api/courses")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"title":"제목","description":null,"price":0}"""),
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.courseId").value(1))
	}

	@Test
	fun `필수값이 없으면 400을 반환한다`() {
		mockMvc.perform(
			post("/api/courses")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"title":"","price":0}"""),
		)
			.andExpect(status().isBadRequest)
	}

	@Test
	fun `존재하지 않는 강의를 조회하면 404를 반환한다`() {
		every { getCourseUseCase.execute(999L) } throws CourseNotFoundException("course not found: 999")

		mockMvc.perform(get("/api/courses/999"))
			.andExpect(status().isNotFound)
	}

	@Test
	fun `강의 목록을 조회하면 200과 목록을 반환한다`() {
		every { listCoursesUseCase.execute(any()) } returns PageResult(emptyList(), 0, 20, 0)

		mockMvc.perform(get("/api/courses"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items").isArray)
	}

	@Test
	fun `잘못된 상태 전이를 요청하면 409를 반환한다`() {
		every { publishCourseUseCase.execute(1L) } throws
			com.classplatform.course.domain.exception.InvalidCourseStatusTransitionException("cannot transition")

		mockMvc.perform(
			patch("/api/courses/1/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"action":"PUBLISH"}"""),
		)
			.andExpect(status().isConflict)
	}
}
