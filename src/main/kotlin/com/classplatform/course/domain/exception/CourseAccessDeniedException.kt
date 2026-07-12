package com.classplatform.course.domain.exception

import com.classplatform.common.exception.ForbiddenActionException

class CourseAccessDeniedException(message: String) : ForbiddenActionException(message)
