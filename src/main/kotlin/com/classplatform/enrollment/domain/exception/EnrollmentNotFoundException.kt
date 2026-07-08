package com.classplatform.enrollment.domain.exception

import com.classplatform.common.exception.ResourceNotFoundException

class EnrollmentNotFoundException(message: String) : ResourceNotFoundException(message)
