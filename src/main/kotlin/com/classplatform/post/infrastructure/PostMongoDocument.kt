package com.classplatform.post.infrastructure

import com.classplatform.post.domain.PostAiStatus
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "posts")
class PostMongoDocument(
	@Id
	val id: String?,
	var title: String,
	var body: String,
	var authorId: Long,
	var aiStatus: PostAiStatus,
	var tags: List<String>,
	var summary: String?,
) {
	@CreatedDate
	var createdAt: Instant? = null

	@LastModifiedDate
	var updatedAt: Instant? = null
}
