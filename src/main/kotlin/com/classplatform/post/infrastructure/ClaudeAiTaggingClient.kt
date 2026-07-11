package com.classplatform.post.infrastructure

import com.anthropic.client.AnthropicClient
import com.anthropic.errors.AnthropicException
import com.anthropic.models.messages.MessageCreateParams
import com.classplatform.common.AiEnrichmentProperties
import com.classplatform.post.domain.AiEnrichmentResult
import com.classplatform.post.domain.AiTaggingPort
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.exception.AiTaggingFailedException
import org.springframework.stereotype.Component

@Component
class ClaudeAiTaggingClient(
	private val anthropicClient: AnthropicClient,
	private val properties: AiEnrichmentProperties,
) : AiTaggingPort {

	override fun generateTagsAndSummary(title: String, body: String): AiEnrichmentResult {
		val params = MessageCreateParams.builder()
			.model(properties.model)
			.maxTokens(MAX_TOKENS)
			.addUserMessage(buildPrompt(title, body))
			.outputConfig(PostEnrichmentResult::class.java)
			.build()

		val parsed = try {
			val message = anthropicClient.messages().create(params)
			message.content().firstOrNull()?.asText()?.text()
				?: throw AiTaggingFailedException("AI 응답에 text 콘텐츠가 없습니다")
		} catch (ex: AnthropicException) {
			throw AiTaggingFailedException("AI 태깅 요청이 실패했습니다: ${ex.message}", ex)
		}

		return AiEnrichmentResult(tags = parsed.tags, summary = parsed.summary)
	}

	private fun buildPrompt(title: String, body: String): String =
		"""
		다음 커뮤니티 게시글을 분석해서 카테고리 태그(최대 ${Post.MAX_TAGS}개)와 요약(최대 ${Post.MAX_SUMMARY_LENGTH}자)을 생성해줘.

		제목: $title
		본문: $body
		""".trimIndent()

	companion object {
		private const val MAX_TOKENS = 1024L
	}
}

internal data class PostEnrichmentResult(
	val tags: List<String>,
	val summary: String,
)
