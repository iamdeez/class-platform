package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreatePostUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val useCase = CreatePostUseCase(postRepository)

	@Test
	fun `게시글을 생성하면 AI 처리 상태는 PENDING이고 태그·요약은 비어 있다`() {
		val savedSlot = slot<Post>()
		every { postRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("제목", "본문", UserId(1L))

		assertEquals("제목", result.title)
		assertEquals("본문", result.body)
		assertEquals(PostAiStatus.PENDING, result.aiStatus)
		assertTrue(result.tags.isEmpty())
		assertEquals(null, result.summary)
	}

	@Test
	fun `본문에 포함된 스크립트 태그는 sanitize되어 저장된다`() {
		val savedSlot = slot<Post>()
		every { postRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

		val result = useCase.execute("제목", "<script>alert(1)</script><p>본문</p>", UserId(1L))

		assertTrue(!result.body.contains("script"))
	}
}
