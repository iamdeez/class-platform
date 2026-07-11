package com.classplatform.post.application

import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostCachePort
import com.classplatform.post.domain.PostPopularityPort
import com.classplatform.post.domain.PostRepository
import com.classplatform.post.domain.exception.PostNotFoundException
import org.springframework.stereotype.Service

@Service
class GetPostUseCase(
	private val postRepository: PostRepository,
	private val postPopularityPort: PostPopularityPort,
	private val postCachePort: PostCachePort,
) {
	fun execute(postId: String): PostDetail {
		val post = loadPost(postId)

		// 조회수 증가는 실패해도 게시글 조회 자체를 막지 않는다(NFR-002 장애 격리).
		runCatching { postPopularityPort.incrementViewCount(postId) }

		val (likeCount, viewCount) = runCatching {
			postPopularityPort.getLikeCount(postId) to postPopularityPort.getViewCount(postId)
		}.getOrDefault(post.likeCount to post.viewCount)

		return PostDetail(post = post, likeCount = likeCount, viewCount = viewCount)
	}

	private fun loadPost(postId: String): Post {
		val cached = runCatching { postCachePort.get(postId) }.getOrNull()
		if (cached != null) {
			return Post.fromSnapshot(cached)
		}

		val post = postRepository.findById(postId) ?: throw PostNotFoundException("post not found: $postId")
		runCatching { postCachePort.put(post.toSnapshot()) }
		return post
	}
}
