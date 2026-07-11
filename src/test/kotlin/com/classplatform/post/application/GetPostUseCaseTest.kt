package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostNotFoundException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GetPostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val useCase = GetPostUseCase(postRepository)

	@Test
	fun `존재하는 게시글을 조회한다`() {
		val post = Post.reconstitute(
			id = "post-1",
			title = "제목",
			body = "본문",
			authorId = UserId(1L),
			aiStatus = PostAiStatus.PENDING,
			tags = emptyList(),
			summary = null,
		)
		every { postRepository.findById("post-1") } returns post

		val result = useCase.execute("post-1")

		assertEquals("post-1", result.id)
	}

	@Test
	fun `존재하지 않는 게시글이면 예외가 발생한다`() {
		every { postRepository.findById("post-1") } returns null

		assertThrows<PostNotFoundException> { useCase.execute("post-1") }
	}
}
