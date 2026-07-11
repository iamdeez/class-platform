package com.classplatform.post.infrastructure

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PopularityCacheSyncSchedulerTest {

	private val postRepository = mockk<PostRepository>()
	private val postPopularityPort = mockk<PostPopularityPort>()
	private val scheduler = PopularityCacheSyncScheduler(postRepository, postPopularityPort)

	private fun storedPost(id: String) = Post.reconstitute(
		id = id,
		title = "제목",
		body = "본문",
		authorId = UserId(1L),
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `dirty set의 게시글들을 모두 최신 좋아요·조회수로 동기화한다`() {
		every { postPopularityPort.consumeDirty() } returns setOf("post-1", "post-2")

		every { postRepository.findById("post-1") } returns storedPost("post-1")
		every { postPopularityPort.getLikeCount("post-1") } returns 3L
		every { postPopularityPort.getViewCount("post-1") } returns 20L

		every { postRepository.findById("post-2") } returns storedPost("post-2")
		every { postPopularityPort.getLikeCount("post-2") } returns 5L
		every { postPopularityPort.getViewCount("post-2") } returns 40L

		val savedSlot = mutableListOf<Post>()
		every { postRepository.save(capture(savedSlot)) } answers { savedSlot.last() }

		scheduler.sync()

		assertEquals(2, savedSlot.size)
		val byId = savedSlot.associateBy { it.id }
		assertEquals(3L, byId["post-1"]?.likeCount)
		assertEquals(20L, byId["post-1"]?.viewCount)
		assertEquals(5L, byId["post-2"]?.likeCount)
		assertEquals(40L, byId["post-2"]?.viewCount)
	}

	@Test
	fun `이미 삭제된 게시글은 건너뛴다`() {
		every { postPopularityPort.consumeDirty() } returns setOf("post-1")
		every { postRepository.findById("post-1") } returns null

		scheduler.sync()

		verify(exactly = 0) { postRepository.save(any()) }
	}

	@Test
	fun `한 게시글 동기화가 실패해도 나머지는 계속 처리한다`() {
		every { postPopularityPort.consumeDirty() } returns setOf("post-1", "post-2")

		every { postRepository.findById("post-1") } returns storedPost("post-1")
		every { postPopularityPort.getLikeCount("post-1") } throws RuntimeException("redis down")

		every { postRepository.findById("post-2") } returns storedPost("post-2")
		every { postPopularityPort.getLikeCount("post-2") } returns 5L
		every { postPopularityPort.getViewCount("post-2") } returns 40L

		val savedSlot = slot<Post>()
		every { postRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		scheduler.sync()

		assertEquals("post-2", savedSlot.captured.id)
		verify(exactly = 1) { postRepository.save(any()) }
	}
}
