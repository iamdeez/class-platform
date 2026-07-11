package com.classplatform.comment.domain

import com.classplatform.common.UserId

class Comment private constructor(
	val id: String?,
	val postId: String,
	val authorId: UserId,
	val content: String,
) {
	init {
		require(content.isNotBlank()) { "content must not be blank" }
	}

	companion object {
		fun write(postId: String, content: String, authorId: UserId): Comment =
			Comment(id = null, postId = postId, authorId = authorId, content = content)

		fun reconstitute(id: String, postId: String, authorId: UserId, content: String): Comment =
			Comment(id, postId, authorId, content)
	}
}
