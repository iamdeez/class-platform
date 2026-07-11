package com.classplatform.post.application

import com.classplatform.post.domain.Post

data class PostDetail(
	val post: Post,
	val likeCount: Long,
	val viewCount: Long,
)
