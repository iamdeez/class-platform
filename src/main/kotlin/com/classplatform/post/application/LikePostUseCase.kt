package com.classplatform.post.application

import com.classplatform.common.UserId
import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostNotFoundException
import org.springframework.stereotype.Service

@Service
class LikePostUseCase(
	private val postRepository: PostRepository,
	private val postPopularityPort: PostPopularityPort,
) {
	fun execute(postId: String, userId: UserId): LikeResult {
		postRepository.findById(postId) ?: throw PostNotFoundException("post not found: $postId")

		postPopularityPort.addLike(postId, userId)
		val likeCount = postPopularityPort.getLikeCount(postId)
		postPopularityPort.refreshRanking(postId, likeCount)
		postPopularityPort.markDirty(postId)

		return LikeResult(liked = true, likeCount = likeCount)
	}
}
