package com.classplatform.statistics.infrastructure

import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.math.BigDecimal

data class CourseStatisticsRow(
	val courseId: Long,
	val title: String,
	val studentCount: Long,
	val revenue: BigDecimal,
	val completedCount: Long,
	val cancelledCount: Long,
	val totalCount: Long,
)

@Mapper
interface CourseStatisticsMapper {
	fun findAllByInstructorId(@Param("instructorId") instructorId: Long): List<CourseStatisticsRow>

	fun findByCourseId(@Param("courseId") courseId: Long): CourseStatisticsRow?
}
