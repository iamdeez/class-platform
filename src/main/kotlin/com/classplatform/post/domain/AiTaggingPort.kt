package com.classplatform.post.domain

interface AiTaggingPort {
	fun generateTagsAndSummary(title: String, body: String): AiEnrichmentResult
}

data class AiEnrichmentResult(
	val tags: List<String>,
	val summary: String,
)
