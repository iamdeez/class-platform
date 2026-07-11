package com.classplatform.post.application

import com.classplatform.common.HtmlSanitizer
import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.event.PostCreatedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class CreatePostUseCase(
	private val postRepository: PostRepository,
	private val eventPublisher: ApplicationEventPublisher,
) {
	fun execute(title: String, body: String, authorId: UserId): Post {
		val post = Post.register(title, HtmlSanitizer.sanitize(body), authorId)
		val saved = postRepository.save(post)
		eventPublisher.publishEvent(PostCreatedEvent(requireNotNull(saved.id), saved.title, saved.body))
		return saved
	}
}
