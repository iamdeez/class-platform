package com.classplatform.course.domain.exception

import com.classplatform.common.exception.InvalidStateException

class InvalidCourseStatusTransitionException(message: String) : InvalidStateException(message)
