package com.classplatform.enrollment.domain.exception

import com.classplatform.common.exception.InvalidStateException

class EnrollmentCancellationNotAllowedException(message: String) : InvalidStateException(message)
