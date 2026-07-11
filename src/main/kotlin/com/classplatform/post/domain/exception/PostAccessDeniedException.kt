package com.classplatform.post.domain.exception

import com.classplatform.common.exception.ForbiddenActionException

class PostAccessDeniedException(message: String) : ForbiddenActionException(message)
