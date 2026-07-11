package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.AiEnrichmentResult
import com.classplatform.post.domain.AiTaggingPort
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.AiTaggingFailedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnrichPostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val aiTaggingPort = mockk<AiTaggingPort>()
	private val useCase = EnrichPostUseCase(postRepository, aiTaggingPort)

	private fun pendingPost() = Post.reconstitute(
		id = "post-1",
		title = "제목",
		body = "본문",
		authorId = UserId(1L),
		aiStatus = PostAiStatus.PENDING,
		tags = emptyList(),
		summary = null,
	)

	@Test
	fun `AI 태깅이 성공하면 게시글이 COMPLETED로 전이된다`() {
		every { postRepository.findById("post-1") } returns pendingPost()
		every { aiTaggingPort.generateTagsAndSummary("제목", "본문") } returns
			AiEnrichmentResult(tags = listOf("스프링", "코틀린"), summary = "요약")
		val savedSlot = slot<Post>()
		every { postRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("post-1", "제목", "본문")

		assertEquals(PostAiStatus.COMPLETED, result?.aiStatus)
		assertEquals(listOf("스프링", "코틀린"), result?.tags)
		assertEquals("요약", result?.summary)
	}

	@Test
	fun `AI 태깅이 실패하면 게시글이 FAILED로 전이되고 게시글 자체는 영향받지 않는다`() {
		every { postRepository.findById("post-1") } returns pendingPost()
		every { aiTaggingPort.generateTagsAndSummary("제목", "본문") } throws
			AiTaggingFailedException("AI 호출 실패")
		val savedSlot = slot<Post>()
		every { postRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("post-1", "제목", "본문")

		assertEquals(PostAiStatus.FAILED, result?.aiStatus)
		assertEquals("제목", result?.title)
		assertEquals("본문", result?.body)
	}

	@Test
	fun `게시글이 존재하지 않으면 아무것도 하지 않는다`() {
		every { postRepository.findById("post-1") } returns null

		val result = useCase.execute("post-1", "제목", "본문")

		assertNull(result)
		verify(exactly = 0) { postRepository.save(any()) }
	}
}
