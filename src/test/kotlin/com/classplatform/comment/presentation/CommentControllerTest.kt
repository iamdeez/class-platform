package com.classplatform.comment.presentation

import com.classplatform.comment.application.CreateCommentUseCase
import com.classplatform.comment.application.DeleteCommentUseCase
import com.classplatform.comment.application.ListCommentsUseCase
import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.exception.CommentAccessDeniedException
import com.classplatform.common.UserId
import com.classplatform.post.domain.exception.PostNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CommentController::class)
class CommentControllerTest {

	@Autowired
	private lateinit var mockMvc: MockMvc

	@MockkBean
	private lateinit var createCommentUseCase: CreateCommentUseCase

	@MockkBean
	private lateinit var listCommentsUseCase: ListCommentsUseCase

	@MockkBean
	private lateinit var deleteCommentUseCase: DeleteCommentUseCase

	private fun sampleComment(id: String = "comment-1", authorId: UserId = UserId(1L)) =
		Comment.reconstitute(id, "post-1", authorId, "댓글 내용")

	@Test
	fun `댓글 작성이 성공하면 201과 commentId를 반환한다`() {
		every { createCommentUseCase.execute("post-1", "댓글 내용", UserId(1L)) } returns sampleComment()

		mockMvc.perform(
			post("/api/posts/post-1/comments")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"content":"댓글 내용"}"""),
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.commentId").value("comment-1"))
	}

	@Test
	fun `내용이 없으면 400을 반환한다`() {
		mockMvc.perform(
			post("/api/posts/post-1/comments")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"content":""}"""),
		)
			.andExpect(status().isBadRequest)
	}

	@Test
	fun `존재하지 않는 게시글에 댓글을 작성하면 404를 반환한다`() {
		every { createCommentUseCase.execute("post-1", "댓글 내용", UserId(1L)) } throws
			PostNotFoundException("post not found: post-1")

		mockMvc.perform(
			post("/api/posts/post-1/comments")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"content":"댓글 내용"}"""),
		)
			.andExpect(status().isNotFound)
	}

	@Test
	fun `게시글의 댓글 목록을 조회하면 200을 반환한다`() {
		every { listCommentsUseCase.execute("post-1") } returns listOf(sampleComment())

		mockMvc.perform(get("/api/posts/post-1/comments"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items").isArray)
			.andExpect(jsonPath("$.data.items[0].content").value("댓글 내용"))
	}

	@Test
	fun `작성자가 댓글을 삭제하면 204를 반환한다`() {
		every { deleteCommentUseCase.execute("comment-1", UserId(1L)) } just runs

		mockMvc.perform(delete("/api/comments/comment-1").header("X-User-Id", "1"))
			.andExpect(status().isNoContent)
	}

	@Test
	fun `작성자가 아니면 삭제 시 403을 반환한다`() {
		every { deleteCommentUseCase.execute("comment-1", UserId(2L)) } throws
			CommentAccessDeniedException("user 2 cannot delete comment comment-1")

		mockMvc.perform(delete("/api/comments/comment-1").header("X-User-Id", "2"))
			.andExpect(status().isForbidden)
	}
}
