package com.classplatform.comment.infrastructure

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "comments")
class CommentMongoDocument(
	@Id
	val id: String?,
	var postId: String,
	var authorId: Long,
	var content: String,
) {
	@CreatedDate
	var createdAt: Instant? = null
}
