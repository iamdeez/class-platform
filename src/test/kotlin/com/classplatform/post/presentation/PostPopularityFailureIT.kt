package com.classplatform.post.presentation

import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.infrastructure.PostMongoRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostPopularityFailureIT {

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
			// Redis 컨테이너를 아예 띄우지 않고, 아무것도 listen하지 않는 포트로 연결을 강제해
			// 캐시·랭킹 접근이 모두 실패하는 상황(SC-007)을 결정론적으로 재현한다.
			registry.add("spring.data.redis.port") { 1 }
			// PopularityCacheSyncScheduler가 테스트 도중 깨진 Redis에 접속을 시도해 커맨드 타임아웃으로
			// 응답 없이 대기하는 것을 막기 위해, 짧은 테스트 실행 시간 동안은 스케줄이 발동하지 않도록 늦춘다.
			registry.add("popularity-cache.sync-interval-ms") { 600_000 }
		}
	}

	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var postRepository: PostRepository

	@Autowired
	private lateinit var postMongoRepository: PostMongoRepository

	@BeforeEach
	fun cleanUp() {
		postMongoRepository.deleteAll()
	}

	@Test
	fun `SC-007 캐시 연결이 불가능해도 게시글 상세 조회는 저장된 스냅샷 값으로 정상 응답한다`() {
		val post = Post.register("제목", "본문", UserId(1L))
		post.applyPopularitySnapshot(likeCount = 3L, viewCount = 99L)
		val savedPost = postRepository.save(post)

		mockMvc.perform(get("/api/posts/${savedPost.id}"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.likeCount").value(3))
			.andExpect(jsonPath("$.data.viewCount").value(99))
	}
}
