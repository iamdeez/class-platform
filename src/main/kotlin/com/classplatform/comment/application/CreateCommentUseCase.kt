package com.classplatform.comment.application

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import com.classplatform.common.HtmlSanitizer
import com.classplatform.common.UserId
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostNotFoundException
import org.springframework.stereotype.Service

@Service
class CreateCommentUseCase(
	private val commentRepository: CommentRepository,
	private val postRepository: PostRepository,
) {
	fun execute(postId: String, content: String, authorId: UserId): Comment {
		postRepository.findById(postId) ?: throw PostNotFoundException("post not found: $postId")
		val comment = Comment.write(postId, HtmlSanitizer.sanitize(content), authorId)
		return commentRepository.save(comment)
	}
}
