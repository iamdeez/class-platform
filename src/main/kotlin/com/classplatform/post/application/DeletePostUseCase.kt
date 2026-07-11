package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostAccessDeniedException
import com.classplatform.post.domain.exception.PostNotFoundException
import org.springframework.stereotype.Service

@Service
class DeletePostUseCase(
	private val postRepository: PostRepository,
) {
	fun execute(postId: String, requesterId: UserId) {
		val post = postRepository.findById(postId) ?: throw PostNotFoundException("post not found: $postId")
		if (post.authorId != requesterId) {
			throw PostAccessDeniedException("user $requesterId cannot delete post $postId")
		}
		postRepository.deleteById(postId)
	}
}
