package com.classplatform.post.presentation.dto

import jakarta.validation.constraints.NotBlank

data class CreatePostRequest(
	@field:NotBlank
	val title: String,
	@field:NotBlank
	val body: String,
)

data class CreatePostResponse(val postId: String)

data class PostResponse(
	val id: String,
	val title: String,
	val body: String,
	val authorId: Long,
	val aiStatus: String,
	val tags: List<String>,
	val summary: String?,
)

data class PostListResponse(
	val items: List<PostResponse>,
	val page: Int,
	val totalCount: Long,
)

data class UpdatePostRequest(
	val title: String?,
	val body: String?,
)
