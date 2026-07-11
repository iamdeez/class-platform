package com.classplatform.post.domain

interface PostCachePort {
	fun get(postId: String): PostSnapshot?

	fun put(snapshot: PostSnapshot)
}
