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
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostPopularIT {

	companion object {
		@Container
		@JvmStatic
		val mysql = MySQLContainer("mysql:8.0")

		@Container
		@JvmStatic
		val mongo = MongoDBContainer("mongo:8.0")

		@Container
		@JvmStatic
		val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)

		@JvmStatic
		@DynamicPropertySource
		fun overrideProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url", mysql::getJdbcUrl)
			registry.add("spring.datasource.username", mysql::getUsername)
			registry.add("spring.datasource.password", mysql::getPassword)
			registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl)
			registry.add("spring.data.redis.host", redis::getHost)
			registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
		}
	}

	@Autowired
	private lateinit var mockMvc: MockMvc

	@Autowired
	private lateinit var postRepository: PostRepository

	@Autowired
	private lateinit var postMongoRepository: PostMongoRepository

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@BeforeEach
	fun cleanUp() {
		postMongoRepository.deleteAll()
		// post:popular 등 랭킹 키는 게시글별로 격리되지 않는 전역 키라, 같은 클래스 내 다른 테스트가
		// 남긴 데이터가 섞이지 않도록 매 테스트 시작 전 Redis 전체를 비운다.
		redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
	}

	@Test
	fun `SC-006 인기 게시글 목록은 좋아요 수 내림차순으로 반환된다`() {
		val postA = postRepository.save(Post.register("A", "본문", UserId(1L)))
		val postB = postRepository.save(Post.register("B", "본문", UserId(1L)))
		postRepository.save(Post.register("C", "본문", UserId(1L)))

		// B: 좋아요 2, A: 좋아요 1, C: 좋아요 0(한 번도 좋아요를 받지 못한 게시글은 랭킹에 아예 등록되지 않는다)
		mockMvc.perform(post("/api/posts/${postB.id}/likes").header("X-User-Id", "10"))
		mockMvc.perform(post("/api/posts/${postB.id}/likes").header("X-User-Id", "11"))
		mockMvc.perform(post("/api/posts/${postA.id}/likes").header("X-User-Id", "10"))

		mockMvc.perform(get("/api/posts/popular"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].postId").value(postB.id))
			.andExpect(jsonPath("$.data.items[0].likeCount").value(2))
			.andExpect(jsonPath("$.data.items[1].postId").value(postA.id))
			.andExpect(jsonPath("$.data.items[1].likeCount").value(1))
	}

	@Test
	fun `SC-006 게시글이 11개 이상이어도 인기 목록은 최대 10건까지만 반환된다`() {
		val posts = (1..11).map { i -> postRepository.save(Post.register("게시글$i", "본문", UserId(1L))) }
		posts.forEachIndexed { index, p ->
			mockMvc.perform(post("/api/posts/${p.id}/likes").header("X-User-Id", (index + 2).toString()))
				.andExpect(status().isOk)
		}

		mockMvc.perform(get("/api/posts/popular"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items.length()").value(10))
	}
}
