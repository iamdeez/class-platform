package com.classplatform.post.domain

import com.classplatform.common.UserId

class Post private constructor(
	val id: String?,
	title: String,
	body: String,
	val authorId: UserId,
	aiStatus: PostAiStatus,
	tags: List<String>,
	summary: String?,
) {
	var title: String = title
		private set
	var body: String = body
		private set
	var aiStatus: PostAiStatus = aiStatus
		private set
	var tags: List<String> = tags
		private set
	var summary: String? = summary
		private set

	init {
		require(title.isNotBlank()) { "title must not be blank" }
		require(body.isNotBlank()) { "body must not be blank" }
	}

	fun updateContent(title: String, body: String) {
		require(title.isNotBlank()) { "title must not be blank" }
		require(body.isNotBlank()) { "body must not be blank" }
		this.title = title
		this.body = body
	}

	fun markEnrichmentCompleted(tags: List<String>, summary: String) {
		require(tags.size <= MAX_TAGS) { "tags must not exceed $MAX_TAGS entries" }
		require(summary.length <= MAX_SUMMARY_LENGTH) { "summary must not exceed $MAX_SUMMARY_LENGTH characters" }
		this.tags = tags
		this.summary = summary
		this.aiStatus = PostAiStatus.COMPLETED
	}

	fun markEnrichmentFailed() {
		this.aiStatus = PostAiStatus.FAILED
	}

	companion object {
		const val MAX_TAGS = 5
		const val MAX_SUMMARY_LENGTH = 200

		fun register(title: String, body: String, authorId: UserId): Post = Post(
			id = null,
			title = title,
			body = body,
			authorId = authorId,
			aiStatus = PostAiStatus.PENDING,
			tags = emptyList(),
			summary = null,
		)

		fun reconstitute(
			id: String,
			title: String,
			body: String,
			authorId: UserId,
			aiStatus: PostAiStatus,
			tags: List<String>,
			summary: String?,
		): Post = Post(id, title, body, authorId, aiStatus, tags, summary)
	}
}
