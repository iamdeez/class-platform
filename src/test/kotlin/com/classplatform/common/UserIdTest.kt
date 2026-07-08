package com.classplatform.common

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class UserIdTest {

	@Test
	fun `양수 값으로 생성하면 성공한다`() {
		val userId = UserId(1L)
		assertEquals(1L, userId.value)
	}

	@Test
	fun `0 이하 값으로 생성하면 예외가 발생한다`() {
		assertThrows<IllegalArgumentException> { UserId(0L) }
		assertThrows<IllegalArgumentException> { UserId(-1L) }
	}
}
