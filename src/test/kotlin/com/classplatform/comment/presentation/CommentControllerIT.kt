package com.classplatform.comment.presentation

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import com.classplatform.comment.infrastructure.CommentMongoRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CommentControllerIT {

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
	private lateinit var commentRepository: CommentRepository

	@Autowired
	private lateinit var postMongoRepository: PostMongoRepository

	@Autowired
	private lateinit var commentMongoRepository: CommentMongoRepository

	@BeforeEach
	fun cleanUp() {
		commentMongoRepository.deleteAll()
		postMongoRepository.deleteAll()
	}

	@Test
	fun `SC-005 존재하지 않는 게시글 ID로 댓글 작성을 요청하면 404를 반환한다`() {
		mockMvc.perform(
			post("/api/posts/nonexistent-post-id/comments")
				.header("X-User-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"content":"댓글 내용"}"""),
		)
			.andExpect(status().isNotFound)
	}

	@Test
	fun `SC-006 작성자가 아닌 사용자가 댓글 삭제를 요청하면 403을 반환한다`() {
		val post = postRepository.save(Post.register("제목", "본문", UserId(1L)))
		val comment = commentRepository.save(Comment.write(requireNotNull(post.id), "댓글 내용", UserId(1L)))

		mockMvc.perform(delete("/api/comments/${comment.id}").header("X-User-Id", "2"))
			.andExpect(status().isForbidden)
	}

	@Test
	fun `SC-011 댓글이 달린 게시글을 조회하면 해당 게시글의 모든 댓글이 목록으로 반환된다`() {
		val post = postRepository.save(Post.register("제목", "본문", UserId(1L)))
		val postId = requireNotNull(post.id)
		commentRepository.save(Comment.write(postId, "첫 댓글", UserId(1L)))
		commentRepository.save(Comment.write(postId, "두 번째 댓글", UserId(2L)))

		mockMvc.perform(get("/api/posts/${post.id}/comments"))
			.andExpect(status().isOk)
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].content").value("첫 댓글"))
			.andExpect(jsonPath("$.data.items[1].content").value("두 번째 댓글"))
	}
}
