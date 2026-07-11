package com.classplatform.post.application

import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRepository
import org.springframework.stereotype.Service

@Service
class ListPopularPostsUseCase(
	private val postRepository: PostRepository,
	private val postPopularityPort: PostPopularityPort,
) {
	fun execute(): List<PopularPost> {
		val rankings = postPopularityPort.getTopPosts(LIMIT)
		if (rankings.isEmpty()) return emptyList()

		val postsById = postRepository.findAllByIds(rankings.map { it.postId }).associateBy { it.id }
		return rankings.mapNotNull { ranking ->
			postsById[ranking.postId]?.let { post ->
				PopularPost(postId = ranking.postId, title = post.title, likeCount = ranking.likeCount)
			}
		}
	}

	companion object {
		private const val LIMIT = 10
	}
}
