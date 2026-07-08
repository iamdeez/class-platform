package com.classplatform.enrollment.domain.exception

import com.classplatform.common.exception.InvalidStateException

class InvalidEnrollmentStatusException(message: String) : InvalidStateException(message)
