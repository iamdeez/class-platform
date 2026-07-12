package com.classplatform

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SC002VerificationTest {

	@Test
	fun `SC-002 검증용 의도적 실패 테스트`() {
		assertTrue(false, "이 테스트는 SC-002(CI 실패 시 병합 차단) 검증을 위해 의도적으로 실패한다")
	}
}
