package com.classplatform.post.presentation

import com.classplatform.common.ApiResponse
import com.classplatform.common.PageRequest
import com.classplatform.common.UserId
import com.classplatform.post.application.CreatePostUseCase
import com.classplatform.post.application.DeletePostUseCase
import com.classplatform.post.application.GetPostUseCase
import com.classplatform.post.application.ListPostsUseCase
import com.classplatform.post.application.UpdatePostUseCase
import com.classplatform.post.domain.Post
import com.classplatform.post.presentation.dto.CreatePostRequest
import com.classplatform.post.presentation.dto.CreatePostResponse
import com.classplatform.post.presentation.dto.PostListResponse
import com.classplatform.post.presentation.dto.PostResponse
import com.classplatform.post.presentation.dto.UpdatePostRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts")
class PostController(
	private val createPostUseCase: CreatePostUseCase,
	private val getPostUseCase: GetPostUseCase,
	private val listPostsUseCase: ListPostsUseCase,
	private val updatePostUseCase: UpdatePostUseCase,
	private val deletePostUseCase: DeletePostUseCase,
) {

	@PostMapping
	fun create(
		@RequestHeader("X-User-Id") userId: Long,
		@Valid @RequestBody request: CreatePostRequest,
	): ResponseEntity<ApiResponse<CreatePostResponse>> {
		val post = createPostUseCase.execute(request.title, request.body, UserId(userId))
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(CreatePostResponse(requireNotNull(post.id))))
	}

	@GetMapping
	fun list(
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "20") size: Int,
	): ResponseEntity<ApiResponse<PostListResponse>> {
		val result = listPostsUseCase.execute(PageRequest(page, size))
		return ResponseEntity.ok(
			ApiResponse.success(
				PostListResponse(
					items = result.items.map { it.toResponse() },
					page = result.page,
					totalCount = result.totalCount,
				),
			),
		)
	}

	@GetMapping("/{postId}")
	fun get(@PathVariable postId: String): ResponseEntity<ApiResponse<PostResponse>> {
		val detail = getPostUseCase.execute(postId)
		return ResponseEntity.ok(ApiResponse.success(detail.post.toResponse()))
	}

	@PatchMapping("/{postId}")
	fun update(
		@PathVariable postId: String,
		@RequestHeader("X-User-Id") userId: Long,
		@RequestBody request: UpdatePostRequest,
	): ResponseEntity<ApiResponse<PostResponse>> {
		val post = updatePostUseCase.execute(postId, request.title, request.body, UserId(userId))
		return ResponseEntity.ok(ApiResponse.success(post.toResponse()))
	}

	@DeleteMapping("/{postId}")
	fun delete(
		@PathVariable postId: String,
		@RequestHeader("X-User-Id") userId: Long,
	): ResponseEntity<Void> {
		deletePostUseCase.execute(postId, UserId(userId))
		return ResponseEntity.noContent().build()
	}

	private fun Post.toResponse() = PostResponse(
		id = requireNotNull(id),
		title = title,
		body = body,
		authorId = authorId.value,
		aiStatus = aiStatus.name,
		tags = tags,
		summary = summary,
	)
}
