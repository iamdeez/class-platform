package com.classplatform

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SC004VerificationTest {

	@Test
	fun `SC-004 검증용 의도적 실패 테스트`() {
		assertTrue(false, "이 테스트는 SC-004(main 강제 병합 시에도 실패 테스트는 배포 차단) 검증을 위해 의도적으로 실패한다")
	}
}
