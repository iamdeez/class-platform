package com.classplatform.post.domain

import com.classplatform.common.PageRequest
import com.classplatform.common.PageResult

interface PostRepository {
	fun save(post: Post): Post

	fun findById(id: String): Post?

	fun findAll(pageRequest: PageRequest): PageResult<Post>
}
