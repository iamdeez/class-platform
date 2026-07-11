package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostCachePort
import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostNotFoundException
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GetPostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val postPopularityPort = mockk<PostPopularityPort>()
	private val postCachePort = mockk<PostCachePort>()
	private val useCase = GetPostUseCase(postRepository, postPopularityPort, postCachePort)

	private fun storedPost(likeCount: Long = 0, viewCount: Long = 0) = Post.reconstitute(
		id = "post-1",
		title = "제목",
		body = "본문",
		authorId = UserId(1L),
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
		likeCount = likeCount,
		viewCount = viewCount,
	)

	@Test
	fun `존재하는 게시글을 조회하면 실시간 좋아요·조회수를 사용하고 조회수 증가를 요청한다`() {
		every { postCachePort.get("post-1") } returns null
		every { postRepository.findById("post-1") } returns storedPost()
		every { postCachePort.put(any()) } just runs
		every { postPopularityPort.incrementViewCount("post-1") } returns 10L
		every { postPopularityPort.getLikeCount("post-1") } returns 7L
		every { postPopularityPort.getViewCount("post-1") } returns 10L

		val result = useCase.execute("post-1")

		assertEquals("post-1", result.post.id)
		assertEquals(7L, result.likeCount)
		assertEquals(10L, result.viewCount)
		verify(exactly = 1) { postPopularityPort.incrementViewCount("post-1") }
	}

	@Test
	fun `PostPopularityPort가 실패하면 저장된 스냅샷 값으로 폴백한다`() {
		every { postCachePort.get("post-1") } returns null
		every { postRepository.findById("post-1") } returns storedPost(likeCount = 3L, viewCount = 99L)
		every { postCachePort.put(any()) } just runs
		every { postPopularityPort.incrementViewCount("post-1") } throws RuntimeException("redis down")
		every { postPopularityPort.getLikeCount("post-1") } throws RuntimeException("redis down")
		every { postPopularityPort.getViewCount("post-1") } throws RuntimeException("redis down")

		val result = useCase.execute("post-1")

		assertEquals(3L, result.likeCount)
		assertEquals(99L, result.viewCount)
	}

	@Test
	fun `존재하지 않는 게시글이면 예외가 발생한다`() {
		every { postCachePort.get("post-1") } returns null
		every { postRepository.findById("post-1") } returns null

		assertThrows<PostNotFoundException> { useCase.execute("post-1") }
	}
}
