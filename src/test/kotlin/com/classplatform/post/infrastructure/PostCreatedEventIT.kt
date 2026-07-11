package com.classplatform.post.infrastructure

import com.classplatform.common.UserId
import com.classplatform.post.application.CreatePostUseCase
import com.classplatform.post.domain.AiEnrichmentResult
import com.classplatform.post.domain.AiTaggingPort
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
class PostCreatedEventIT {

	companion object {
		@Container
		@JvmStatic
		val mysql = MySQLContainer("mysql:8.0")

		@Container
		@JvmStatic
		val mongo = MongoDBContainer("mongo:8.0")

		@JvmStatic
		@DynamicPropertySource
		fun overrideProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url", mysql::getJdbcUrl)
			registry.add("spring.datasource.username", mysql::getUsername)
			registry.add("spring.datasource.password", mysql::getPassword)
			registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl)
		}
	}

	@Autowired
	private lateinit var createPostUseCase: CreatePostUseCase

	@Autowired
	private lateinit var postRepository: PostRepository

	@MockkBean
	private lateinit var aiTaggingPort: AiTaggingPort

	@Test
	fun `게시글 작성은 AI 처리 완료를 기다리지 않고 즉시 반환되며, AI 처리는 비동기로 이어져 완료된다`() {
		every { aiTaggingPort.generateTagsAndSummary(any(), any()) } answers {
			Thread.sleep(1000)
			AiEnrichmentResult(tags = listOf("태그"), summary = "요약")
		}

		lateinit var postId: String
		val elapsedMillis = measureTimeMillis {
			val post = createPostUseCase.execute("제목", "본문", UserId(1L))
			assertEquals(PostAiStatus.PENDING, post.aiStatus)
			postId = requireNotNull(post.id)
		}
		assertTrue(elapsedMillis < 500, "게시글 생성 응답이 AI 처리 완료를 기다리면 안 된다: ${elapsedMillis}ms")

		val deadline = System.currentTimeMillis() + 3_000
		var finalStatus = PostAiStatus.PENDING
		while (System.currentTimeMillis() < deadline) {
			finalStatus = requireNotNull(postRepository.findById(postId)).aiStatus
			if (finalStatus != PostAiStatus.PENDING) break
			Thread.sleep(100)
		}
		assertEquals(PostAiStatus.COMPLETED, finalStatus)
	}
}
