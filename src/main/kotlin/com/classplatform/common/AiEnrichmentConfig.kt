package com.classplatform.common

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@EnableAsync
@EnableConfigurationProperties(AiEnrichmentProperties::class)
class AiEnrichmentConfig {

	@Bean
	fun anthropicClient(): AnthropicClient = AnthropicOkHttpClient.fromEnv()
}

@ConfigurationProperties(prefix = "ai-enrichment")
data class AiEnrichmentProperties(
	val model: String = "claude-haiku-4-5",
)
