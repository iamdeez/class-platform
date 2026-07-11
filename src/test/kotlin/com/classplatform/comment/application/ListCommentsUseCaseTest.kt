package com.classplatform.comment.application

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import com.classplatform.common.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ListCommentsUseCaseTest {

	private val commentRepository = mockk<CommentRepository>()
	private val useCase = ListCommentsUseCase(commentRepository)

	@Test
	fun `저장소가 반환한 댓글 목록을 그대로 전달한다`() {
		val comments = listOf(
			Comment.reconstitute("comment-1", "post-1", UserId(1L), "첫 댓글"),
			Comment.reconstitute("comment-2", "post-1", UserId(2L), "두 번째 댓글"),
		)
		every { commentRepository.findAllByPostId("post-1") } returns comments

		val result = useCase.execute("post-1")

		assertEquals(comments, result)
	}
}
