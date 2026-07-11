package com.classplatform.post.application

import com.classplatform.post.domain.AiTaggingPort
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import org.springframework.stereotype.Service

@Service
class EnrichPostUseCase(
	private val postRepository: PostRepository,
	private val aiTaggingPort: AiTaggingPort,
) {
	fun execute(postId: String, title: String, body: String): Post? {
		val post = postRepository.findById(postId) ?: return null

		// NFR-004(장애 격리) 대상: SDK 예외뿐 아니라 AI 응답이 도메인 불변식(태그 개수·요약 길이)을 어겨
		// markEnrichmentCompleted()가 던지는 경우까지 포괄해 FAILED로 흡수한다. 실패 사유와 무관하게
		// 게시글 자체의 조회·수정·삭제는 영향받지 않아야 하기 때문이다.
		try {
			val result = aiTaggingPort.generateTagsAndSummary(title, body)
			post.markEnrichmentCompleted(result.tags, result.summary)
		} catch (ex: Exception) {
			post.markEnrichmentFailed()
		}

		return postRepository.save(post)
	}
}
