package com.classplatform.post.infrastructure

import com.classplatform.post.application.EnrichPostUseCase
import com.classplatform.post.domain.event.PostCreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PostCreatedEventListener(
	private val enrichPostUseCase: EnrichPostUseCase,
) {
	@Async
	@EventListener
	fun onPostCreated(event: PostCreatedEvent) {
		enrichPostUseCase.execute(event.postId, event.title, event.body)
	}
}
