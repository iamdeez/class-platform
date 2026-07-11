package com.classplatform.comment.application

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import com.classplatform.comment.domain.exception.CommentAccessDeniedException
import com.classplatform.comment.domain.exception.CommentNotFoundException
import com.classplatform.common.UserId
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DeleteCommentUseCaseTest {

	private val commentRepository = mockk<CommentRepository>()
	private val useCase = DeleteCommentUseCase(commentRepository)

	private fun existingComment(authorId: UserId) =
		Comment.reconstitute("comment-1", "post-1", authorId, "댓글 내용")

	@Test
	fun `작성자가 댓글을 삭제한다`() {
		every { commentRepository.findById("comment-1") } returns existingComment(UserId(1L))
		every { commentRepository.deleteById("comment-1") } just runs

		useCase.execute("comment-1", UserId(1L))

		verify(exactly = 1) { commentRepository.deleteById("comment-1") }
	}

	@Test
	fun `존재하지 않는 댓글이면 예외가 발생한다`() {
		every { commentRepository.findById("comment-1") } returns null

		assertThrows<CommentNotFoundException> { useCase.execute("comment-1", UserId(1L)) }
	}

	@Test
	fun `작성자가 아니면 예외가 발생한다`() {
		every { commentRepository.findById("comment-1") } returns existingComment(UserId(1L))

		assertThrows<CommentAccessDeniedException> { useCase.execute("comment-1", UserId(2L)) }
	}
}
