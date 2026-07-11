package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
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
import kotlin.test.assertTrue

class LikePostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val postPopularityPort = mockk<PostPopularityPort>()
	private val useCase = LikePostUseCase(postRepository, postPopularityPort)

	private fun existingPost() = Post.reconstitute(
		id = "post-1",
		title = "제목",
		body = "본문",
		authorId = UserId(1L),
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `좋아요를 누르면 현재 좋아요 수와 함께 liked true를 반환한다`() {
		every { postRepository.findById("post-1") } returns existingPost()
		every { postPopularityPort.addLike("post-1", UserId(2L)) } returns true
		every { postPopularityPort.getLikeCount("post-1") } returns 3L
		every { postPopularityPort.refreshRanking("post-1", 3L) } just runs
		every { postPopularityPort.markDirty("post-1") } just runs

		val result = useCase.execute("post-1", UserId(2L))

		assertTrue(result.liked)
		assertEquals(3L, result.likeCount)
		verify(exactly = 1) { postPopularityPort.refreshRanking("post-1", 3L) }
		verify(exactly = 1) { postPopularityPort.markDirty("post-1") }
	}

	@Test
	fun `존재하지 않는 게시글이면 예외가 발생한다`() {
		every { postRepository.findById("post-1") } returns null

		assertThrows<PostNotFoundException> { useCase.execute("post-1", UserId(2L)) }
	}
}
