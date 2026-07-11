package com.classplatform.comment.infrastructure

import org.springframework.data.mongodb.repository.MongoRepository

interface CommentMongoRepository : MongoRepository<CommentMongoDocument, String> {
	fun findTop100ByPostIdOrderByCreatedAtAsc(postId: String): List<CommentMongoDocument>
}
