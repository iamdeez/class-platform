package com.classplatform.post.infrastructure

import com.classplatform.common.PageRequest
import com.classplatform.common.UserId
import com.classplatform.common.config.MongoAuditingConfig
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostAiStatus
import com.classplatform.post.domain.PostRepository
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
@Import(PostRepositoryImpl::class, MongoAuditingConfig::class)
class PostRepositoryImplIT {

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
	private lateinit var postRepository: PostRepository

	@Autowired
	private lateinit var mongoRepository: PostMongoRepository

	@BeforeEach
	fun cleanUp() {
		mongoRepository.deleteAll()
	}

	@Test
	fun `저장 후 조회하면 동일한 게시글 정보를 반환한다`() {
		val post = Post.register(
			title = "첫 게시글",
			body = "본문 내용입니다",
			authorId = UserId(1L),
		)

		val saved = postRepository.save(post)

		assertNotNull(saved.id)
		val found = postRepository.findById(saved.id!!)
		assertNotNull(found)
		assertEquals("첫 게시글", found.title)
		assertEquals(PostAiStatus.PENDING, found.aiStatus)
	}

	@Test
	fun `목록 조회는 최신 작성순으로 정렬되어 반환된다`() {
		postRepository.save(Post.register("첫 번째", "본문1", UserId(1L)))
		postRepository.save(Post.register("두 번째", "본문2", UserId(1L)))
		postRepository.save(Post.register("세 번째", "본문3", UserId(1L)))

		val result = postRepository.findAll(PageRequest(page = 0, size = 10))

		assertEquals(3, result.items.size)
		assertEquals("세 번째", result.items[0].title)
		assertEquals("두 번째", result.items[1].title)
		assertEquals("첫 번째", result.items[2].title)
	}

	@Test
	fun `좋아요·조회수 스냅샷을 저장하고 조회하면 그대로 유지된다`() {
		val post = Post.register("제목", "본문", UserId(1L))
		post.applyPopularitySnapshot(likeCount = 5L, viewCount = 42L)

		val saved = postRepository.save(post)

		val found = postRepository.findById(saved.id!!)
		assertNotNull(found)
		assertEquals(5L, found.likeCount)
		assertEquals(42L, found.viewCount)
	}

	@Test
	fun `findAllByIds는 요청한 ID들의 게시글을 모두 반환한다`() {
		val saved1 = postRepository.save(Post.register("첫 번째", "본문1", UserId(1L)))
		val saved2 = postRepository.save(Post.register("두 번째", "본문2", UserId(1L)))
		postRepository.save(Post.register("세 번째", "본문3", UserId(1L)))

		val result = postRepository.findAllByIds(listOf(saved1.id!!, saved2.id!!))

		assertEquals(2, result.size)
		assertEquals(setOf("첫 번째", "두 번째"), result.map { it.title }.toSet())
	}
}
