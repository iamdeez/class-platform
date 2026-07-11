package com.classplatform.comment.presentation.dto

import jakarta.validation.constraints.NotBlank

data class CreateCommentRequest(
	@field:NotBlank
	val content: String,
)

data class CreateCommentResponse(val commentId: String)

data class CommentResponse(
	val id: String,
	val postId: String,
	val authorId: Long,
	val content: String,
)

data class CommentListResponse(
	val items: List<CommentResponse>,
)
