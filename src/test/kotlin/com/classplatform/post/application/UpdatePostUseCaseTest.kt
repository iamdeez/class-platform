package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostAccessDeniedException
import com.classplatform.post.domain.exception.PostNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class UpdatePostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val useCase = UpdatePostUseCase(postRepository)

	private fun existingPost(authorId: UserId) = Post.reconstitute(
		id = "post-1",
		title = "원래 제목",
		body = "원래 본문",
		authorId = authorId,
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `작성자가 게시글을 수정한다`() {
		every { postRepository.findById("post-1") } returns existingPost(UserId(1L))
		val savedSlot = slot<Post>()
		every { postRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("post-1", "새 제목", "새 본문", UserId(1L))

		assertEquals("새 제목", result.title)
		assertEquals("새 본문", result.body)
	}

	@Test
	fun `존재하지 않는 게시글이면 예외가 발생한다`() {
		every { postRepository.findById("post-1") } returns null

		assertThrows<PostNotFoundException> { useCase.execute("post-1", "새 제목", "새 본문", UserId(1L)) }
	}

	@Test
	fun `작성자가 아니면 예외가 발생한다`() {
		every { postRepository.findById("post-1") } returns existingPost(UserId(1L))

		assertThrows<PostAccessDeniedException> { useCase.execute("post-1", "새 제목", "새 본문", UserId(2L)) }
	}
}
