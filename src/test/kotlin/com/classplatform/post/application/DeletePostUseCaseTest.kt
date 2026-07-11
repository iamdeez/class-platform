package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostAccessDeniedException
import com.classplatform.post.domain.exception.PostNotFoundException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DeletePostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val useCase = DeletePostUseCase(postRepository)

	private fun existingPost(authorId: UserId) = Post.reconstitute(
		id = "post-1",
		title = "제목",
		body = "본문",
		authorId = authorId,
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `작성자가 게시글을 삭제한다`() {
		every { postRepository.findById("post-1") } returns existingPost(UserId(1L))
		every { postRepository.deleteById("post-1") } just runs

		useCase.execute("post-1", UserId(1L))

		verify(exactly = 1) { postRepository.deleteById("post-1") }
	}

	@Test
	fun `존재하지 않는 게시글이면 예외가 발생한다`() {
		every { postRepository.findById("post-1") } returns null

		assertThrows<PostNotFoundException> { useCase.execute("post-1", UserId(1L)) }
	}

	@Test
	fun `작성자가 아니면 예외가 발생한다`() {
		every { postRepository.findById("post-1") } returns existingPost(UserId(1L))

		assertThrows<PostAccessDeniedException> { useCase.execute("post-1", UserId(2L)) }
	}
}
