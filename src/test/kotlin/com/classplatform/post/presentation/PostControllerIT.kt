package com.classplatform.post.presentation

import com.classplatform.common.UserId
import com.classplatform.post.domain.AiEnrichmentResult
import com.classplatform.post.domain.AiTaggingPort
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.infrastructure.PostMongoRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostControllerIT {

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
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var postRepository: PostRepository

	@Autowired
	private lateinit var postMongoRepository: PostMongoRepository

	@MockkBean
	private lateinit var aiTaggingPort: AiTaggingPort

	@BeforeEach
	fun cleanUp() {
		postMongoRepository.deleteAll()
	}

	@Test
	fun `SC-002 게시글 작성 요청은 AI 처리 완료를 기다리지 않고 즉시 201 응답을 반환한다`() {
		every { aiTaggingPort.generateTagsAndSummary(any(), any()) } answers {
			Thread.sleep(1_000)
			AiEnrichmentResult(tags = listOf("태그"), summary = "요약")
		}

		val elapsedMillis = measureTimeMillis {
			mockMvc.perform(
				post("/api/posts")
					.header("X-User-Id", "1")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""{"title":"제목","body":"본문"}"""),
			)
				.andExpect(status().isCreated)
		}

		assertTrue(elapsedMillis < 500, "게시글 등록 응답이 AI 처리 완료를 기다리면 안 된다: ${elapsedMillis}ms")

		// 테스트가 끝나며 컨텍스트가 정리되기 전에 백그라운드 @Async 리스너가 마무리되도록 잠시 대기한다.
		// 그렇지 않으면 Mongo 커넥션이 닫힌 뒤 리스너가 뒤늦게 저장을 시도하며 에러 로그가 남는다.
		Thread.sleep(1_200)
	}

	@Test
	fun `SC-003 게시글 목록 조회 결과는 최신 작성순으로 정렬되어 반환된다`() {
		postRepository.save(Post.register("첫 번째", "본문1", UserId(1L)))
		postRepository.save(Post.register("두 번째", "본문2", UserId(1L)))
		postRepository.save(Post.register("세 번째", "본문3", UserId(1L)))

		mockMvc.perform(get("/api/posts"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items[0].title").value("세 번째"))
			.andExpect(jsonPath("$.data.items[1].title").value("두 번째"))
			.andExpect(jsonPath("$.data.items[2].title").value("첫 번째"))
	}

	@Test
	fun `SC-010 존재하지 않는 게시글을 상세 조회하면 404를 반환한다`() {
		mockMvc.perform(get("/api/posts/nonexistent-post-id"))
			.andExpect(status().isNotFound)
	}
}
