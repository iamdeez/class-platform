package com.classplatform.post.domain.event

data class PostCreatedEvent(
	val postId: String,
	val title: String,
	val body: String,
)
