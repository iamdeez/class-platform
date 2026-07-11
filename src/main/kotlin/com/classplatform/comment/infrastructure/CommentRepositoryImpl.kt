package com.classplatform.comment.infrastructure

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import com.classplatform.common.UserId
import org.springframework.stereotype.Repository

@Repository
class CommentRepositoryImpl(
	private val mongoRepository: CommentMongoRepository,
) : CommentRepository {

	override fun save(comment: Comment): Comment {
		val saved = mongoRepository.save(comment.toDocument())
		return saved.toDomain()
	}

	override fun findById(id: String): Comment? =
		mongoRepository.findById(id).orElse(null)?.toDomain()

	override fun findAllByPostId(postId: String): List<Comment> =
		mongoRepository.findTop100ByPostIdOrderByCreatedAtAsc(postId).map { it.toDomain() }

	override fun deleteById(id: String) {
		mongoRepository.deleteById(id)
	}

	private fun Comment.toDocument(): CommentMongoDocument = CommentMongoDocument(
		id = id,
		postId = postId,
		authorId = authorId.value,
		content = content,
	)

	private fun CommentMongoDocument.toDomain(): Comment = Comment.reconstitute(
		id = requireNotNull(id) { "persisted CommentMongoDocument must have an id" },
		postId = postId,
		authorId = UserId(authorId),
		content = content,
	)
}
