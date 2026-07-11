package com.classplatform.post.infrastructure

import com.classplatform.post.domain.PostCachePort
import com.classplatform.post.domain.PostSnapshot
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisPostCacheAdapter(
	private val redisTemplate: StringRedisTemplate,
	private val objectMapper: ObjectMapper,
	@Value("\${post-detail-cache.ttl-seconds:300}") private val ttlSeconds: Long,
) : PostCachePort {

	override fun get(postId: String): PostSnapshot? {
		val json = redisTemplate.opsForValue().get(cacheKey(postId)) ?: return null
		return objectMapper.readValue(json, PostSnapshot::class.java)
	}

	override fun put(snapshot: PostSnapshot) {
		val json = objectMapper.writeValueAsString(snapshot)
		redisTemplate.opsForValue().set(cacheKey(snapshot.id), json, Duration.ofSeconds(ttlSeconds))
	}

	private fun cacheKey(postId: String) = "post:cache:$postId"
}
