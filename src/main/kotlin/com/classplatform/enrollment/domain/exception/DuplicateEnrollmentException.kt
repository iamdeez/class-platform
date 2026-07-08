package com.classplatform.enrollment.domain.exception

import com.classplatform.common.exception.DuplicateResourceException

class DuplicateEnrollmentException(message: String) : DuplicateResourceException(message)
