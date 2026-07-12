package com.classplatform.enrollment.application

import com.classplatform.common.UserId
import com.classplatform.enrollment.domain.Enrollment
import com.classplatform.enrollment.domain.EnrollmentRepository
import com.classplatform.enrollment.domain.EnrollmentStatus
import org.springframework.stereotype.Service

@Service
class ListMyEnrollmentsUseCase(
	private val enrollmentRepository: EnrollmentRepository,
) {
	fun execute(userId: UserId): List<Enrollment> =
		enrollmentRepository.findAllByUserId(userId).filter { it.status != EnrollmentStatus.CANCELLED }
}
