package com.classplatform.post.application

import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostNotFoundException
import org.springframework.stereotype.Service

@Service
class GetPostUseCase(
	private val postRepository: PostRepository,
) {
	fun execute(postId: String): Post =
		postRepository.findById(postId) ?: throw PostNotFoundException("post not found: $postId")
}
