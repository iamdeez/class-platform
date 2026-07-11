package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.event.PostCreatedEvent
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreatePostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
	private val useCase = CreatePostUseCase(postRepository, eventPublisher)

	private fun stubSaveAssigningId(id: String = "post-1"): CapturingSlot<Post> {
		val savedSlot = slot<Post>()
		every { postRepository.save(capture(savedSlot)) } answers {
			val transient = savedSlot.captured
			Post.reconstitute(
				id = id,
				title = transient.title,
				body = transient.body,
				authorId = transient.authorId,
				aiStatus = transient.aiStatus,
				tags = transient.tags,
				summary = transient.summary,
			)
		}
		return savedSlot
	}

	@Test
	fun `게시글을 생성하면 AI 처리 상태는 PENDING이고 태그·요약은 비어 있다`() {
		stubSaveAssigningId()

		val result = useCase.execute("제목", "본문", UserId(1L))

		assertEquals("제목", result.title)
		assertEquals("본문", result.body)
		assertEquals(PostAiStatus.PENDING, result.aiStatus)
		assertTrue(result.tags.isEmpty())
		assertEquals(null, result.summary)
	}

	@Test
	fun `본문에 포함된 스크립트 태그는 sanitize되어 저장된다`() {
		stubSaveAssigningId()

		val result = useCase.execute("제목", "<script>alert(1)</script><p>본문</p>", UserId(1L))

		assertTrue(!result.body.contains("script"))
	}

	@Test
	fun `저장 직후 PostCreatedEvent를 발행한다`() {
		stubSaveAssigningId(id = "post-1")

		useCase.execute("제목", "본문", UserId(1L))

		verify(exactly = 1) {
			eventPublisher.publishEvent(PostCreatedEvent("post-1", "제목", "본문"))
		}
	}
}
