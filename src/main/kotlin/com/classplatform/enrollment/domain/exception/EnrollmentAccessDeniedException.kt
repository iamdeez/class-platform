package com.classplatform.enrollment.domain.exception

import com.classplatform.common.exception.ForbiddenActionException

class EnrollmentAccessDeniedException(message: String) : ForbiddenActionException(message)
