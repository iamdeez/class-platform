package com.classplatform.post.application

import com.classplatform.common.HtmlSanitizer
import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import org.springframework.stereotype.Service

@Service
class CreatePostUseCase(
	private val postRepository: PostRepository,
) {
	fun execute(title: String, body: String, authorId: UserId): Post {
		val post = Post.register(title, HtmlSanitizer.sanitize(body), authorId)
		return postRepository.save(post)
	}
}
