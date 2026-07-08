package com.classplatform.common

data class PageRequest(val page: Int, val size: Int) {
	init {
		require(page >= 0) { "page must not be negative: $page" }
		require(size in 1..100) { "size must be between 1 and 100: $size" }
	}
}
