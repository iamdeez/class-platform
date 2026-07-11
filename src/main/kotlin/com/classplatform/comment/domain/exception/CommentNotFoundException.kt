package com.classplatform.comment.domain.exception

import com.classplatform.common.exception.ResourceNotFoundException

class CommentNotFoundException(message: String) : ResourceNotFoundException(message)
