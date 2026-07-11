package com.classplatform.post.application

import com.classplatform.common.PageRequest
import com.classplatform.common.PageResult
import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ListPostsUseCaseTest {

	private val postRepository = mockk<PostRepository>()
	private val useCase = ListPostsUseCase(postRepository)

	@Test
	fun `저장소가 반환한 페이지 결과를 그대로 전달한다`() {
		val post = Post.reconstitute(
			id = "post-1",
			title = "제목",
			body = "본문",
			authorId = UserId(1L),
			aiStatus = PostAiStatus.PENDING,
			tags = emptyList(),
			summary = null,
		)
		val pageRequest = PageRequest(page = 0, size = 10)
		val pageResult = PageResult(items = listOf(post), page = 0, size = 10, totalCount = 1L)
		every { postRepository.findAll(pageRequest) } returns pageResult

		val result = useCase.execute(pageRequest)

		assertEquals(pageResult, result)
	}
}
