package com.classplatform.post.infrastructure

import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PopularityCacheSyncScheduler(
	private val postRepository: PostRepository,
	private val postPopularityPort: PostPopularityPort,
) {

	@Scheduled(
		fixedDelayString = "\${popularity-cache.sync-interval-ms}",
		initialDelayString = "\${popularity-cache.sync-interval-ms}",
	)
	fun sync() {
		postPopularityPort.consumeDirty().forEach { postId ->
			// 배치 내 한 게시글 동기화가 실패해도 나머지 게시글 처리를 계속한다.
			runCatching { syncOne(postId) }
		}
	}

	private fun syncOne(postId: String) {
		val post = postRepository.findById(postId) ?: return
		val likeCount = postPopularityPort.getLikeCount(postId)
		val viewCount = postPopularityPort.getViewCount(postId)
		post.applyPopularitySnapshot(likeCount, viewCount)
		postRepository.save(post)
	}
}
