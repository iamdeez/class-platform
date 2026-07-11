package com.classplatform.post

import com.classplatform.common.UserId
import com.classplatform.post.application.LikePostUseCase
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@Testcontainers
@SpringBootTest
class PostLikeConcurrencyIT {

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
	private lateinit var likePostUseCase: LikePostUseCase

	@Autowired
	private lateinit var postRepository: PostRepository

	@Autowired
	private lateinit var postPopularityPort: PostPopularityPort

	@Test
	fun `SC-008 서로 다른 사용자 100명이 동시에 좋아요를 눌러도 최종 좋아요 수는 100이다`() {
		val savedPost = postRepository.save(Post.register("동시성 테스트 게시글", "본문", UserId(1L)))
		val postId = requireNotNull(savedPost.id)

		val threadCount = 100
		val executor = Executors.newFixedThreadPool(threadCount)
		val readyLatch = CountDownLatch(threadCount)
		val startLatch = CountDownLatch(1)
		val doneLatch = CountDownLatch(threadCount)
		val unexpectedErrors = CopyOnWriteArrayList<Throwable>()

		repeat(threadCount) { index ->
			executor.submit {
				readyLatch.countDown()
				startLatch.await()
				try {
					likePostUseCase.execute(postId, UserId((index + 2).toLong()))
				} catch (ex: Throwable) {
					unexpectedErrors.add(ex)
				} finally {
					doneLatch.countDown()
				}
			}
		}

		readyLatch.await(10, TimeUnit.SECONDS)
		startLatch.countDown()
		doneLatch.await(60, TimeUnit.SECONDS)
		executor.shutdown()

		assertEquals(emptyList<Throwable>(), unexpectedErrors)
		assertEquals(100L, postPopularityPort.getLikeCount(postId))
	}
}
