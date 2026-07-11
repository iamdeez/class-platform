package com.classplatform.comment.application

import com.classplatform.comment.domain.CommentRepository
import com.classplatform.comment.domain.exception.CommentAccessDeniedException
import com.classplatform.comment.domain.exception.CommentNotFoundException
import com.classplatform.common.UserId
import org.springframework.stereotype.Service

@Service
class DeleteCommentUseCase(
	private val commentRepository: CommentRepository,
) {
	fun execute(commentId: String, requesterId: UserId) {
		val comment = commentRepository.findById(commentId)
			?: throw CommentNotFoundException("comment not found: $commentId")
		if (comment.authorId != requesterId) {
			throw CommentAccessDeniedException("user $requesterId cannot delete comment $commentId")
		}
		commentRepository.deleteById(commentId)
	}
}
