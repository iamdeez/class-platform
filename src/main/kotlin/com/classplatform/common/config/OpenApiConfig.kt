package com.classplatform.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

	@Bean
	fun classPlatformOpenAPI(): OpenAPI = OpenAPI()
		.info(
			Info()
				.title("class-platform API")
				.description("월부닷컴 채용공고 대비 포트폴리오 연습 프로젝트 — 온라인 클래스 플랫폼 API")
				.version("v0.1.0"),
		)
}
