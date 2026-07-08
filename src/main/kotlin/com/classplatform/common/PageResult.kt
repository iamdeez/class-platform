package com.classplatform.common

data class PageResult<T>(
	val items: List<T>,
	val page: Int,
	val size: Int,
	val totalCount: Long,
)
