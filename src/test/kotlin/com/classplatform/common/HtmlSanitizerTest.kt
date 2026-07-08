package com.classplatform.common

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlSanitizerTest {

	@Test
	fun `script 태그는 제거된다`() {
		val result = HtmlSanitizer.sanitize("""<p>안녕</p><script>alert('xss')</script>""")

		assertFalse(result.contains("<script"))
		assertTrue(result.contains("안녕"))
	}

	@Test
	fun `onerror 같은 이벤트 핸들러 속성은 제거된다`() {
		val result = HtmlSanitizer.sanitize("""<img src="https://example.com/a.png" onerror="alert('xss')">""")

		assertFalse(result.contains("onerror"))
		assertTrue(result.contains("""src="https://example.com/a.png""""))
	}

	@Test
	fun `javascript 프로토콜 src는 제거된다`() {
		val result = HtmlSanitizer.sanitize("""<img src="javascript:alert('xss')">""")

		assertFalse(result.contains("javascript:"))
	}

	@Test
	fun `figure·img·style·class 등 강의 상세 콘텐츠에 필요한 서식은 유지된다`() {
		val html = """<figure class="image"><img style="aspect-ratio:855/1807;" """ +
			"""src="https://cdn.weolbu.com/a.png" width="855" height="1807"></figure>"""

		val result = HtmlSanitizer.sanitize(html)

		assertTrue(result.contains("<figure"))
		assertTrue(result.contains("""class="image""""))
		assertTrue(result.contains("aspect-ratio"))
		assertTrue(result.contains("""src="https://cdn.weolbu.com/a.png""""))
	}
}
