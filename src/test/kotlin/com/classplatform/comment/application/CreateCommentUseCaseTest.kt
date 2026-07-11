package com.classplatform.comment.application

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateCommentUseCaseTest {

	private val commentRepository = mockk<CommentRepository>()
	private val postRepository = mockk<PostRepository>()
	private val useCase = CreateCommentUseCase(commentRepository, postRepository)

	private fun existingPost() = Post.reconstitute(
		id = "post-1",
		title = "제목",
		body = "본문",
		authorId = UserId(1L),
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `존재하는 게시글에 댓글을 작성한다`() {
		every { postRepository.findById("post-1") } returns existingPost()
		val savedSlot = slot<Comment>()
		every { commentRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("post-1", "댓글 내용", UserId(2L))

		assertEquals("post-1", result.postId)
		assertEquals("댓글 내용", result.content)
	}

	@Test
	fun `존재하지 않는 게시글에 댓글을 작성하면 예외가 발생한다`() {
		every { postRepository.findById("post-1") } returns null

		assertThrows<PostNotFoundException> { useCase.execute("post-1", "댓글 내용", UserId(2L)) }
	}

	@Test
	fun `본문에 포함된 스크립트 태그는 sanitize되어 저장된다`() {
		every { postRepository.findById("post-1") } returns existingPost()
		val savedSlot = slot<Comment>()
		every { commentRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("post-1", "<script>alert(1)</script>댓글", UserId(2L))

		assertTrue(!result.content.contains("script"))
	}
}
