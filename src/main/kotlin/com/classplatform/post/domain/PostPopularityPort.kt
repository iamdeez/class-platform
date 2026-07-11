package com.classplatform.post.domain

import com.classplatform.common.UserId

interface PostPopularityPort {
	fun addLike(postId: String, userId: UserId): Boolean

	fun removeLike(postId: String, userId: UserId): Boolean

	fun isLiked(postId: String, userId: UserId): Boolean

	fun getLikeCount(postId: String): Long

	fun incrementViewCount(postId: String): Long

	fun getViewCount(postId: String): Long

	fun refreshRanking(postId: String, likeCount: Long)

	fun getTopPostIds(limit: Int): List<String>

	fun markDirty(postId: String)

	fun consumeDirty(): Set<String>
}
