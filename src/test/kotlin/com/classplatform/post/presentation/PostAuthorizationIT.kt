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
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PostAuthorizationIT {

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

	@BeforeEach
	fun cleanUp() {
		postMongoRepository.deleteAll()
	}

	@Test
	fun `SC-004 작성자가 아닌 사용자가 게시글 수정을 요청하면 403을 반환한다`() {
		val post = postRepository.save(Post.register("제목", "본문", UserId(1L)))

		mockMvc.perform(
			patch("/api/posts/${post.id}")
				.header("X-User-Id", "2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"title":"수정된 제목","body":"수정된 본문"}"""),
		)
			.andExpect(status().isForbidden)
	}

	@Test
	fun `SC-004 작성자가 아닌 사용자가 게시글 삭제를 요청하면 403을 반환한다`() {
		val post = postRepository.save(Post.register("제목", "본문", UserId(1L)))

		mockMvc.perform(delete("/api/posts/${post.id}").header("X-User-Id", "2"))
			.andExpect(status().isForbidden)
	}
}
