package com.classplatform.enrollment.presentation

import com.classplatform.common.UserId
import com.classplatform.enrollment.application.CancelEnrollmentUseCase
import com.classplatform.enrollment.application.EnrollUseCase
import com.classplatform.enrollment.application.ListMyEnrollmentsUseCase
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentStatus
import com.classplatform.enrollment.domain.exception.EnrollmentAccessDeniedException
import com.classplatform.enrollment.domain.exception.EnrollmentNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(EnrollmentController::class)
class EnrollmentControllerTest {

	@Autowired
	private lateinit var mockMvc: MockMvc

	@MockkBean
	private lateinit var enrollUseCase: EnrollUseCase

	@MockkBean
	private lateinit var cancelEnrollmentUseCase: CancelEnrollmentUseCase

	@MockkBean
	private lateinit var listMyEnrollmentsUseCase: ListMyEnrollmentsUseCase

	@Test
	fun `수강 신청이 성공하면 201과 enrollmentId를 반환한다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), EnrollmentStatus.ACTIVE)
		every { enrollUseCase.execute(10L, UserId(2L)) } returns enrollment

		mockMvc.perform(post("/api/courses/10/enrollments").header("X-User-Id", "2"))
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.enrollmentId").value(1))
	}

	@Test
	fun `타인의 신청을 취소하면 403을 반환한다`() {
		every { cancelEnrollmentUseCase.execute(1L, UserId(3L)) } throws
			EnrollmentAccessDeniedException("forbidden")

		mockMvc.perform(delete("/api/enrollments/1").header("X-User-Id", "3"))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `존재하지 않는 신청을 취소하면 404를 반환한다`() {
		every { cancelEnrollmentUseCase.execute(999L, UserId(2L)) } throws
			EnrollmentNotFoundException("not found")

		mockMvc.perform(delete("/api/enrollments/999").header("X-User-Id", "2"))
			.andExpect(status().isNotFound)
	}

	@Test
	fun `정상 취소 요청은 204를 반환한다`() {
		val enrollment = Enrollment.reconstitute(1L, 10L, UserId(2L), EnrollmentStatus.CANCELLED)
		every { cancelEnrollmentUseCase.execute(1L, UserId(2L)) } returns enrollment

		mockMvc.perform(delete("/api/enrollments/1").header("X-User-Id", "2"))
			.andExpect(status().isNoContent)
	}

	@Test
	fun `내 신청 목록을 조회하면 200을 반환한다`() {
		every { listMyEnrollmentsUseCase.execute(UserId(2L)) } returns emptyList()

		mockMvc.perform(get("/api/users/me/enrollments").header("X-User-Id", "2"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items").isArray)
	}
}
