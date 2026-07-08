package com.classplatform.course.domain.exception

import com.classplatform.common.exception.InvalidStateException

class CourseNotEnrollableException(message: String) : InvalidStateException(message)
