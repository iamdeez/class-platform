package com.classplatform.course.domain.exception

import com.classplatform.common.exception.ResourceNotFoundException

class CourseNotFoundException(message: String) : ResourceNotFoundException(message)
