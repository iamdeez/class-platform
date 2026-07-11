package com.classplatform.post.application

import com.classplatform.common.HtmlSanitizer
import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostAccessDeniedException
import com.classplatform.post.domain.exception.PostNotFoundException
import org.springframework.stereotype.Service

@Service
class UpdatePostUseCase(
	private val postRepository: PostRepository,
) {
	fun execute(postId: String, title: String, body: String, requesterId: UserId): Post {
		val post = postRepository.findById(postId) ?: throw PostNotFoundException("post not found: $postId")
		if (post.authorId != requesterId) {
			throw PostAccessDeniedException("user $requesterId cannot update post $postId")
		}
		post.updateContent(title, HtmlSanitizer.sanitize(body))
		return postRepository.save(post)
	}
}
