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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
class PostLikeIT {

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

	@BeforeEach
	fun cleanUp() {
		postMongoRepository.deleteAll()
	}

	@Test
	fun `SC-001 동일 사용자가 좋아요를 두 번 요청해도 좋아요 수는 1만 증가한다`() {
		val savedPost = postRepository.save(Post.register("제목", "본문", UserId(1L)))

		mockMvc.perform(post("/api/posts/${savedPost.id}/likes").header("X-User-Id", "10"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.likeCount").value(1))

		mockMvc.perform(post("/api/posts/${savedPost.id}/likes").header("X-User-Id", "10"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.likeCount").value(1))
	}

	@Test
	fun `SC-002 좋아요 후 취소하면 좋아요 수가 원래대로 돌아온다`() {
		val savedPost = postRepository.save(Post.register("제목", "본문", UserId(1L)))

		mockMvc.perform(post("/api/posts/${savedPost.id}/likes").header("X-User-Id", "10"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.likeCount").value(1))

		mockMvc.perform(delete("/api/posts/${savedPost.id}/likes").header("X-User-Id", "10"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.liked").value(false))
			.andExpect(jsonPath("$.data.likeCount").value(0))
	}
}
