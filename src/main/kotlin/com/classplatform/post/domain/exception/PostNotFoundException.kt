package com.classplatform.post.domain.exception

import com.classplatform.common.exception.ResourceNotFoundException

class PostNotFoundException(message: String) : ResourceNotFoundException(message)
