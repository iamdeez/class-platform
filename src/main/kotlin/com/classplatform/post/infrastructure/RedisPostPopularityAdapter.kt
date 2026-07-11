package com.classplatform.post.infrastructure

import com.classplatform.common.UserId
import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRanking
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisPostPopularityAdapter(
	private val redisTemplate: StringRedisTemplate,
) : PostPopularityPort {

	override fun addLike(postId: String, userId: UserId): Boolean =
		(redisTemplate.opsForSet().add(likeKey(postId), userId.value.toString()) ?: 0L) > 0

	override fun removeLike(postId: String, userId: UserId): Boolean =
		(redisTemplate.opsForSet().remove(likeKey(postId), userId.value.toString()) ?: 0L) > 0

	override fun isLiked(postId: String, userId: UserId): Boolean =
		redisTemplate.opsForSet().isMember(likeKey(postId), userId.value.toString()) ?: false

	override fun getLikeCount(postId: String): Long =
		redisTemplate.opsForSet().size(likeKey(postId)) ?: 0L

	override fun incrementViewCount(postId: String): Long =
		redisTemplate.opsForValue().increment(viewKey(postId)) ?: 0L

	override fun getViewCount(postId: String): Long =
		redisTemplate.opsForValue().get(viewKey(postId))?.toLong() ?: 0L

	override fun refreshRanking(postId: String, likeCount: Long) {
		redisTemplate.opsForZSet().add(POPULAR_KEY, postId, likeCount.toDouble())
	}

	override fun getTopPosts(limit: Int): List<PostRanking> =
		redisTemplate.opsForZSet()
			.reverseRangeWithScores(POPULAR_KEY, 0, (limit - 1).toLong())
			?.mapNotNull { tuple ->
				val postId = tuple.value ?: return@mapNotNull null
				val score = tuple.score ?: return@mapNotNull null
				PostRanking(postId, score.toLong())
			}
			?: emptyList()

	override fun markDirty(postId: String) {
		redisTemplate.opsForSet().add(DIRTY_KEY, postId)
	}

	override fun consumeDirty(): Set<String> {
		val members = redisTemplate.opsForSet().members(DIRTY_KEY) ?: emptySet()
		if (members.isNotEmpty()) {
			redisTemplate.opsForSet().remove(DIRTY_KEY, *members.toTypedArray())
		}
		return members
	}

	private fun likeKey(postId: String) = "post:like:$postId"

	private fun viewKey(postId: String) = "post:view:$postId"

	companion object {
		private const val POPULAR_KEY = "post:popular"
		private const val DIRTY_KEY = "post:dirty"
	}
}
