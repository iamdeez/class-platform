package com.classplatform.comment.domain

interface CommentRepository {
	fun save(comment: Comment): Comment

	fun findById(id: String): Comment?

	fun findAllByPostId(postId: String): List<Comment>

	fun deleteById(id: String)
}
