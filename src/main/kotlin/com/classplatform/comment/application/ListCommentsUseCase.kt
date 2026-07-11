package com.classplatform.comment.application

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import org.springframework.stereotype.Service

@Service
class ListCommentsUseCase(
	private val commentRepository: CommentRepository,
) {
	fun execute(postId: String): List<Comment> = commentRepository.findAllByPostId(postId)
}
