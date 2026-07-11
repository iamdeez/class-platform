package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRanking
import com.classplatform.post.domain.PostRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListPopularPostsUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val postPopularityPort = mockk<PostPopularityPort>()
	private val useCase = ListPopularPostsUseCase(postRepository, postPopularityPort)

	private fun post(id: String, title: String) = Post.reconstitute(
		id = id,
		title = title,
		body = "본문",
		authorId = UserId(1L),
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `Redis 랭킹 순서를 유지하며 배치 조회 1회로 목록을 구성한다`() {
		every { postPopularityPort.getTopPosts(10) } returns listOf(
			PostRanking("post-2", 5L),
			PostRanking("post-1", 3L),
		)
		every { postRepository.findAllByIds(listOf("post-2", "post-1")) } returns
			listOf(post("post-1", "첫 번째"), post("post-2", "두 번째"))

		val result = useCase.execute()

		assertEquals(
			listOf(
				PopularPost("post-2", "두 번째", 5L),
				PopularPost("post-1", "첫 번째", 3L),
			),
			result,
		)
		verify(exactly = 1) { postRepository.findAllByIds(any()) }
	}

	@Test
	fun `랭킹이 비어 있으면 저장소를 조회하지 않고 빈 목록을 반환한다`() {
		every { postPopularityPort.getTopPosts(10) } returns emptyList()

		val result = useCase.execute()

		assertTrue(result.isEmpty())
		verify(exactly = 0) { postRepository.findAllByIds(any()) }
	}
}
