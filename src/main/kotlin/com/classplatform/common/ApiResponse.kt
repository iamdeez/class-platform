package com.classplatform.common

data class ApiResponse<T>(
	val data: T? = null,
	val error: ApiError? = null,
) {
	companion object {
		fun <T> success(data: T): ApiResponse<T> = ApiResponse(data = data)

		fun error(code: String, message: String): ApiResponse<Nothing> =
			ApiResponse(error = ApiError(code, message))
	}
}

data class ApiError(
	val code: String,
	val message: String,
)
