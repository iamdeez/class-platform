package com.classplatform.post.domain

import com.classplatform.common.UserId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PostTest {

	@Test
	fun `제목이 비어 있으면 게시글 생성이 거부된다`() {
		assertThrows<IllegalArgumentException> {
			Post.register(title = "", body = "본문", authorId = UserId(1L))
		}
	}

	@Test
	fun `본문이 비어 있으면 게시글 생성이 거부된다`() {
		assertThrows<IllegalArgumentException> {
			Post.register(title = "제목", body = "", authorId = UserId(1L))
		}
	}

	@Test
	fun `제목과 본문이 공백만으로 이루어져도 게시글 생성이 거부된다`() {
		assertThrows<IllegalArgumentException> {
			Post.register(title = "   ", body = "   ", authorId = UserId(1L))
		}
	}

	@Test
	fun `게시글을 생성하면 좋아요·조회수는 0에서 시작한다`() {
		val post = Post.register(title = "제목", body = "본문", authorId = UserId(1L))

		assertEquals(0L, post.likeCount)
		assertEquals(0L, post.viewCount)
	}

	@Test
	fun `좋아요·조회수 스냅샷을 반영한다`() {
		val post = Post.register(title = "제목", body = "본문", authorId = UserId(1L))

		post.applyPopularitySnapshot(likeCount = 5L, viewCount = 42L)

		assertEquals(5L, post.likeCount)
		assertEquals(42L, post.viewCount)
	}

	@Test
	fun `좋아요 수가 음수면 스냅샷 반영이 거부된다`() {
		val post = Post.register(title = "제목", body = "본문", authorId = UserId(1L))

		assertThrows<IllegalArgumentException> {
			post.applyPopularitySnapshot(likeCount = -1L, viewCount = 0L)
		}
	}

	@Test
	fun `조회수가 음수면 스냅샷 반영이 거부된다`() {
		val post = Post.register(title = "제목", body = "본문", authorId = UserId(1L))

		assertThrows<IllegalArgumentException> {
			post.applyPopularitySnapshot(likeCount = 0L, viewCount = -1L)
		}
	}
}
