package com.classplatform.post.domain

import com.classplatform.common.UserId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
}
