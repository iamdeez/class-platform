package com.classplatform.comment.presentation

import com.classplatform.comment.application.CreateCommentUseCase
import com.classplatform.comment.application.DeleteCommentUseCase
import com.classplatform.comment.application.ListCommentsUseCase
import com.classplatform.comment.domain.Comment
import com.classplatform.comment.presentation.dto.CommentListResponse
import com.classplatform.comment.presentation.dto.CommentResponse
import com.classplatform.comment.presentation.dto.CreateCommentRequest
import com.classplatform.comment.presentation.dto.CreateCommentResponse
import com.classplatform.common.ApiResponse
import com.classplatform.common.UserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CommentController(
	private val createCommentUseCase: CreateCommentUseCase,
	private val listCommentsUseCase: ListCommentsUseCase,
	private val deleteCommentUseCase: DeleteCommentUseCase,
) {

	@PostMapping("/api/posts/{postId}/comments")
	fun create(
		@PathVariable postId: String,
		@RequestHeader("X-User-Id") userId: Long,
		@Valid @RequestBody request: CreateCommentRequest,
	): ResponseEntity<ApiResponse<CreateCommentResponse>> {
		val comment = createCommentUseCase.execute(postId, request.content, UserId(userId))
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(CreateCommentResponse(requireNotNull(comment.id))))
	}

	@GetMapping("/api/posts/{postId}/comments")
	fun list(@PathVariable postId: String): ResponseEntity<ApiResponse<CommentListResponse>> {
		val comments = listCommentsUseCase.execute(postId)
		return ResponseEntity.ok(ApiResponse.success(CommentListResponse(comments.map { it.toResponse() })))
	}

	@DeleteMapping("/api/comments/{commentId}")
	fun delete(
		@PathVariable commentId: String,
		@RequestHeader("X-User-Id") userId: Long,
	): ResponseEntity<Void> {
		deleteCommentUseCase.execute(commentId, UserId(userId))
		return ResponseEntity.noContent().build()
	}

	private fun Comment.toResponse() = CommentResponse(
		id = requireNotNull(id),
		postId = postId,
		authorId = authorId.value,
		content = content,
	)
}
