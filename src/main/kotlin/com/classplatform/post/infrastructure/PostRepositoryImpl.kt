package com.classplatform.post.infrastructure

import com.classplatform.common.PageResult
import com.classplatform.common.UserId
import com.classplatform.post.domain.Post
import com.classplatform.post.domain.PostRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import org.springframework.data.domain.PageRequest as SpringPageRequest
import com.classplatform.common.PageRequest as DomainPageRequest

@Repository
class PostRepositoryImpl(
	private val mongoRepository: PostMongoRepository,
) : PostRepository {

	override fun save(post: Post): Post {
		val document = post.toDocument()
		// Mongo save()는 문서 전체를 덮어쓰므로, 수정 시 createdAt을 유지하기 위해 기존 값을 보존한다.
		if (post.id != null) {
			document.createdAt = mongoRepository.findById(post.id).orElse(null)?.createdAt
		}
		val saved = mongoRepository.save(document)
		return saved.toDomain()
	}

	override fun findById(id: String): Post? =
		mongoRepository.findById(id).orElse(null)?.toDomain()

	override fun findAll(pageRequest: DomainPageRequest): PageResult<Post> {
		val pageable = SpringPageRequest.of(
			pageRequest.page,
			pageRequest.size,
			Sort.by(Sort.Direction.DESC, "createdAt"),
		)
		val page = mongoRepository.findAll(pageable)
		return PageResult(
			items = page.content.map { it.toDomain() },
			page = pageRequest.page,
			size = pageRequest.size,
			totalCount = page.totalElements,
		)
	}

	override fun findAllByIds(ids: List<String>): List<Post> =
		mongoRepository.findAllById(ids).map { it.toDomain() }

	override fun deleteById(id: String) {
		mongoRepository.deleteById(id)
	}

	private fun Post.toDocument(): PostMongoDocument = PostMongoDocument(
		id = id,
		title = title,
		body = body,
		authorId = authorId.value,
		aiStatus = aiStatus,
		tags = tags,
		summary = summary,
		likeCount = likeCount,
		viewCount = viewCount,
	)

	private fun PostMongoDocument.toDomain(): Post = Post.reconstitute(
		id = requireNotNull(id) { "persisted PostMongoDocument must have an id" },
		title = title,
		body = body,
		authorId = UserId(authorId),
		aiStatus = aiStatus,
		tags = tags,
		summary = summary,
		likeCount = likeCount,
		viewCount = viewCount,
	)
}
