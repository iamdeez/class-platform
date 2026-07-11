package com.classplatform.post.infrastructure

import com.anthropic.client.AnthropicClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.models.messages.StructuredContentBlock
import com.anthropic.models.messages.StructuredMessage
import com.anthropic.models.messages.StructuredMessageCreateParams
import com.anthropic.models.messages.StructuredTextBlock
import com.anthropic.services.blocking.MessageService
import com.classplatform.common.AiEnrichmentProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import com.classplatform.post.domain.exception.AiTaggingFailedException

class ClaudeAiTaggingClientTest {

	private val messageService = mockk<MessageService>()
	private val anthropicClient = mockk<AnthropicClient> {
		every { messages() } returns messageService
	}
	private val client = ClaudeAiTaggingClient(anthropicClient, AiEnrichmentProperties())

	@Test
	fun `Claude 응답이 정상이면 태그와 요약을 반환한다`() {
		val parsed = PostEnrichmentResult(tags = listOf("스프링", "코틀린"), summary = "요약 내용")
		val textBlock = mockk<StructuredTextBlock<PostEnrichmentResult>> {
			every { text() } returns parsed
		}
		val contentBlock = mockk<StructuredContentBlock<PostEnrichmentResult>> {
			every { asText() } returns textBlock
		}
		val message = mockk<StructuredMessage<PostEnrichmentResult>> {
			every { content() } returns listOf(contentBlock)
		}
		every { messageService.create(any<StructuredMessageCreateParams<PostEnrichmentResult>>()) } returns message

		val result = client.generateTagsAndSummary("제목", "본문")

		assertEquals(listOf("스프링", "코틀린"), result.tags)
		assertEquals("요약 내용", result.summary)
	}

	@Test
	fun `Claude API 호출이 실패하면 AiTaggingFailedException으로 변환한다`() {
		every {
			messageService.create(any<StructuredMessageCreateParams<PostEnrichmentResult>>())
		} throws AnthropicIoException("connection timed out")

		assertThrows<AiTaggingFailedException> { client.generateTagsAndSummary("제목", "본문") }
	}
}
