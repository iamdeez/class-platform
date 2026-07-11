package com.classplatform.enrollment.infrastructure

import com.classplatform.common.UserId
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.exception.DuplicateEnrollmentException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class EnrollmentRepositoryImpl(
	private val jpaRepository: EnrollmentJpaRepository,
) : EnrollmentRepository {

	override fun save(enrollment: Enrollment): Enrollment {
		val entity = enrollment.toEntity()
		// saveAndFlush로 즉시 INSERT를 내보내야 유니크 제약 위반이 이 메서드 안에서 터진다.
		// save()만 쓰면 실제 INSERT가 트랜잭션 끝까지 지연될 수 있어 여기서 못 잡는다.
		val saved = try {
			jpaRepository.saveAndFlush(entity)
		} catch (ex: DataIntegrityViolationException) {
			throw DuplicateEnrollmentException(
				"user ${enrollment.userId.value} already enrolled in course ${enrollment.courseId}",
			)
		}
		return saved.toDomain()
	}

	override fun findById(id: Long): Enrollment? =
		jpaRepository.findById(id).orElse(null)?.toDomain()

	override fun findAllByUserId(userId: UserId): List<Enrollment> =
		jpaRepository.findAllByUserId(userId.value).map { it.toDomain() }

	private fun Enrollment.toEntity(): EnrollmentJpaEntity = EnrollmentJpaEntity(
		id = id,
		courseId = courseId,
		userId = userId.value,
		price = price,
		status = status,
	)

	private fun EnrollmentJpaEntity.toDomain(): Enrollment = Enrollment.reconstitute(
		id = requireNotNull(id) { "persisted EnrollmentJpaEntity must have an id" },
		courseId = courseId,
		userId = UserId(userId),
		price = price,
		status = status,
	)
}
