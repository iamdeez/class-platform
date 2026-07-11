package com.classplatform.comment.domain.exception

import com.classplatform.common.exception.ForbiddenActionException

class CommentAccessDeniedException(message: String) : ForbiddenActionException(message)
