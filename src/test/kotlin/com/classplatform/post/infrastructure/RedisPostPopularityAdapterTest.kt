package com.classplatform.post.infrastructure

import com.classplatform.common.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisPostPopularityAdapterTest {

	private val setOps = mockk<SetOperations<String, String>>()
	private val valueOps = mockk<ValueOperations<String, String>>()
	private val zSetOps = mockk<ZSetOperations<String, String>>()
	private val redisTemplate = mockk<StringRedisTemplate> {
		every { opsForSet() } returns setOps
		every { opsForValue() } returns valueOps
		every { opsForZSet() } returns zSetOps
	}
	private val adapter = RedisPostPopularityAdapter(redisTemplate)

	@Test
	fun `addLike은 신규 추가 시 true를 반환한다`() {
		every { setOps.add("post:like:post-1", "1") } returns 1L

		assertTrue(adapter.addLike("post-1", UserId(1L)))
	}

	@Test
	fun `addLike은 이미 좋아요한 경우 false를 반환한다`() {
		every { setOps.add("post:like:post-1", "1") } returns 0L

		assertFalse(adapter.addLike("post-1", UserId(1L)))
	}

	@Test
	fun `removeLike은 실제로 제거된 경우에만 true를 반환한다`() {
		every { setOps.remove("post:like:post-1", "1") } returns 1L

		assertTrue(adapter.removeLike("post-1", UserId(1L)))
	}

	@Test
	fun `isLiked은 SISMEMBER 결과를 그대로 반환한다`() {
		every { setOps.isMember("post:like:post-1", "1") } returns true

		assertTrue(adapter.isLiked("post-1", UserId(1L)))
	}

	@Test
	fun `getLikeCount은 SCARD 결과를 반환하고 null이면 0이다`() {
		every { setOps.size("post:like:post-1") } returns 3L

		assertEquals(3L, adapter.getLikeCount("post-1"))
	}

	@Test
	fun `incrementViewCount은 INCR 결과를 반환한다`() {
		every { valueOps.increment("post:view:post-1") } returns 5L

		assertEquals(5L, adapter.incrementViewCount("post-1"))
	}

	@Test
	fun `getViewCount은 저장된 값이 없으면 0을 반환한다`() {
		every { valueOps.get("post:view:post-1") } returns null

		assertEquals(0L, adapter.getViewCount("post-1"))
	}

	@Test
	fun `refreshRanking은 좋아요 수를 절대값으로 ZADD한다`() {
		every { zSetOps.add("post:popular", "post-1", 7.0) } returns true

		adapter.refreshRanking("post-1", 7L)

		verify(exactly = 1) { zSetOps.add("post:popular", "post-1", 7.0) }
	}

	@Test
	fun `getTopPostIds은 ZREVRANGE 결과 순서를 그대로 유지한다`() {
		every { zSetOps.reverseRange("post:popular", 0, 9) } returns linkedSetOf("post-2", "post-1")

		val result = adapter.getTopPostIds(10)

		assertEquals(listOf("post-2", "post-1"), result)
	}

	@Test
	fun `consumeDirty는 대상이 없으면 빈 집합을 반환하고 SREM을 호출하지 않는다`() {
		every { setOps.members("post:dirty") } returns emptySet()

		val result = adapter.consumeDirty()

		assertTrue(result.isEmpty())
		verify(exactly = 0) { setOps.remove("post:dirty", *anyVararg()) }
	}

	@Test
	fun `consumeDirty는 조회한 멤버를 반환하고 제거한다`() {
		every { setOps.members("post:dirty") } returns setOf("post-1", "post-2")
		every { setOps.remove("post:dirty", *anyVararg()) } returns 2L

		val result = adapter.consumeDirty()

		assertEquals(setOf("post-1", "post-2"), result)
		verify(exactly = 1) { setOps.remove("post:dirty", *anyVararg()) }
	}
}
