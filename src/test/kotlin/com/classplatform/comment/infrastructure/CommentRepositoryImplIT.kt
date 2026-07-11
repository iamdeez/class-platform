package com.classplatform.comment.infrastructure

import com.classplatform.comment.domain.Comment
import com.classplatform.comment.domain.CommentRepository
import com.classplatform.common.UserId
import com.classplatform.common.config.MongoAuditingConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Testcontainers
@DataMongoTest
@Import(CommentRepositoryImpl::class, MongoAuditingConfig::class)
class CommentRepositoryImplIT {

	companion object {
		@Container
		@JvmStatic
		val mongo = MongoDBContainer("mongo:8.0")

		@JvmStatic
		@DynamicPropertySource
		fun overrideProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl)
		}
	}

	@Autowired
	private lateinit var commentRepository: CommentRepository

	@Autowired
	private lateinit var mongoRepository: CommentMongoRepository

	@BeforeEach
	fun cleanUp() {
		mongoRepository.deleteAll()
	}

	@Test
	fun `저장 후 조회하면 동일한 댓글 정보를 반환한다`() {
		val comment = Comment.write(postId = "post-1", content = "댓글 내용", authorId = UserId(1L))

		val saved = commentRepository.save(comment)

		assertNotNull(saved.id)
		val found = commentRepository.findById(saved.id!!)
		assertNotNull(found)
		assertEquals("댓글 내용", found.content)
		assertEquals("post-1", found.postId)
	}

	@Test
	fun `게시글 ID로 댓글 목록을 조회하면 작성 순서대로 반환된다`() {
		commentRepository.save(Comment.write("post-1", "첫 댓글", UserId(1L)))
		commentRepository.save(Comment.write("post-1", "두 번째 댓글", UserId(2L)))
		commentRepository.save(Comment.write("post-2", "다른 게시글 댓글", UserId(1L)))

		val result = commentRepository.findAllByPostId("post-1")

		assertEquals(2, result.size)
		assertEquals("첫 댓글", result[0].content)
		assertEquals("두 번째 댓글", result[1].content)
	}

	@Test
	fun `삭제하면 더 이상 조회되지 않는다`() {
		val saved = commentRepository.save(Comment.write("post-1", "댓글", UserId(1L)))

		commentRepository.deleteById(saved.id!!)

		assertEquals(null, commentRepository.findById(saved.id!!))
	}
}
