package com.classplatform.post.domain

data class PostSnapshot(
	val id: String,
	val title: String,
	val body: String,
	val authorId: Long,
	val aiStatus: PostAiStatus,
	val tags: List<String>,
	val summary: String?,
	val likeCount: Long,
	val viewCount: Long,
)
