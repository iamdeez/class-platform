package com.classplatform.post.presentation

import com.classplatform.common.PageResult
import com.classplatform.common.UserId
import com.classplatform.post.application.CreatePostUseCase
import com.classplatform.post.application.DeletePostUseCase
import com.classplatform.post.application.GetPostUseCase
import com.classplatform.post.application.ListPostsUseCase
import com.classplatform.post.application.PostDetail
import com.classplatform.post.application.UpdatePostUseCase
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.exception.PostAccessDeniedException
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PostController::class)
class PostControllerTest {

	@Autowired
	private lateinit var mockMvc: MockMvc

	@MockkBean
	private lateinit var createPostUseCase: CreatePostUseCase

	@MockkBean
	private lateinit var getPostUseCase: GetPostUseCase

	@MockkBean
	private lateinit var listPostsUseCase: ListPostsUseCase

	@MockkBean
	private lateinit var updatePostUseCase: UpdatePostUseCase

	@MockkBean
	private lateinit var deletePostUseCase: DeletePostUseCase

	private fun samplePost(id: String = "post-1", authorId: UserId = UserId(1L)) = Post.reconstitute(
		id = id,
		title = "제목",
		body = "본문",
		authorId = authorId,
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `게시글 작성 요청이 성공하면 201과 postId를 반환한다`() {
		// UserId는 value class라 any()가 임의 값을 생성하다 require(value > 0)에 걸릴 수 있어 명시 매칭한다.
		every { createPostUseCase.execute(any(), any(), UserId(1L)) } returns samplePost()

		mockMvc.perform(
			post("/api/posts")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"title":"제목","body":"본문"}"""),
		)
			.andExpect(status().isCreated)
			.andExpect(jsonPath("$.data.postId").value("post-1"))
	}

	@Test
	fun `필수값이 없으면 400을 반환한다`() {
		mockMvc.perform(
			post("/api/posts")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"title":"","body":""}"""),
		)
			.andExpect(status().isBadRequest)
	}

	@Test
	fun `게시글 목록을 조회하면 200과 목록을 반환한다`() {
		every { listPostsUseCase.execute(any()) } returns PageResult(listOf(samplePost()), 0, 20, 1)

		mockMvc.perform(get("/api/posts"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items").isArray)
			.andExpect(jsonPath("$.data.items[0].aiStatus").value("PENDING"))
	}

	@Test
	fun `존재하는 게시글을 조회하면 200을 반환한다`() {
		every { getPostUseCase.execute("post-1") } returns PostDetail(samplePost(), likeCount = 0L, viewCount = 0L)

		mockMvc.perform(get("/api/posts/post-1"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.id").value("post-1"))
	}

	@Test
	fun `존재하지 않는 게시글을 조회하면 404를 반환한다`() {
		every { getPostUseCase.execute("post-1") } throws PostNotFoundException("post not found: post-1")

		mockMvc.perform(get("/api/posts/post-1"))
			.andExpect(status().isNotFound)
	}

	@Test
	fun `작성자가 게시글을 수정하면 200을 반환한다`() {
		every { updatePostUseCase.execute("post-1", "새 제목", "새 본문", UserId(1L)) } returns samplePost()

		mockMvc.perform(
			patch("/api/posts/post-1")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"title":"새 제목","body":"새 본문"}"""),
		)
			.andExpect(status().isOk)
	}

	@Test
	fun `작성자가 아니면 수정 시 403을 반환한다`() {
		every { updatePostUseCase.execute("post-1", "새 제목", "새 본문", UserId(2L)) } throws
			PostAccessDeniedException("user 2 cannot update post post-1")

		mockMvc.perform(
			patch("/api/posts/post-1")
				.header("X-User-Id", "2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"title":"새 제목","body":"새 본문"}"""),
		)
			.andExpect(status().isForbidden)
	}

	@Test
	fun `작성자가 게시글을 삭제하면 204를 반환한다`() {
		every { deletePostUseCase.execute("post-1", UserId(1L)) } just runs

		mockMvc.perform(delete("/api/posts/post-1").header("X-User-Id", "1"))
			.andExpect(status().isNoContent)
	}

	@Test
	fun `작성자가 아니면 삭제 시 403을 반환한다`() {
		every { deletePostUseCase.execute("post-1", UserId(2L)) } throws
			PostAccessDeniedException("user 2 cannot delete post post-1")

		mockMvc.perform(delete("/api/posts/post-1").header("X-User-Id", "2"))
			.andExpect(status().isForbidden)
	}
}
