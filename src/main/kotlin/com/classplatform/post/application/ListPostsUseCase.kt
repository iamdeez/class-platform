package com.classplatform.post.application

import com.classplatform.common.PageRequest
import com.classplatform.common.PageResult
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import org.springframework.stereotype.Service

@Service
class ListPostsUseCase(
	private val postRepository: PostRepository,
) {
	fun execute(pageRequest: PageRequest): PageResult<Post> =
		postRepository.findAll(pageRequest)
}
