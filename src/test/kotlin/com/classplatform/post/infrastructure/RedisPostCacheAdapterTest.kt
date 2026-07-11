package com.classplatform.post.infrastructure

import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostSnapshot
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedisPostCacheAdapterTest {

	private val valueOps = mockk<ValueOperations<String, String>>()
	private val redisTemplate = mockk<StringRedisTemplate> {
		every { opsForValue() } returns valueOps
	}
	private val objectMapper: ObjectMapper = jacksonObjectMapper()
	private val adapter = RedisPostCacheAdapter(redisTemplate, objectMapper, ttlSeconds = 300)

	private fun sampleSnapshot() = PostSnapshot(
		id = "post-1",
		title = "제목",
		body = "본문",
		authorId = 1L,
		aiStatus = PostAiStatus.COMPLETED,
		tags = listOf("태그"),
		summary = "요약",
		likeCount = 3L,
		viewCount = 10L,
	)

	@Test
	fun `캐시 미스 시 null을 반환한다`() {
		every { valueOps.get("post:cache:post-1") } returns null

		assertNull(adapter.get("post-1"))
	}

	@Test
	fun `캐시 히트 시 저장된 스냅샷을 역직렬화해 반환한다`() {
		val snapshot = sampleSnapshot()
		every { valueOps.get("post:cache:post-1") } returns objectMapper.writeValueAsString(snapshot)

		val result = adapter.get("post-1")

		assertEquals(snapshot, result)
	}

	@Test
	fun `put은 TTL과 함께 직렬화된 값을 저장한다`() {
		val snapshot = sampleSnapshot()
		val valueSlot = slot<String>()
		every { valueOps.set("post:cache:post-1", capture(valueSlot), Duration.ofSeconds(300)) } returns Unit

		adapter.put(snapshot)

		verify(exactly = 1) { valueOps.set("post:cache:post-1", any(), Duration.ofSeconds(300)) }
		assertEquals(snapshot, objectMapper.readValue(valueSlot.captured, PostSnapshot::class.java))
	}
}
