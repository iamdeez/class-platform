package com.classplatform.common.exception

// 각 bounded context의 구체 예외는 이 중 하나를 상속한다 — 핸들러는 카테고리만 안다
abstract class DomainException(message: String) : RuntimeException(message)

open class ResourceNotFoundException(message: String) : DomainException(message)

open class DuplicateResourceException(message: String) : DomainException(message)

open class InvalidStateException(message: String) : DomainException(message)

open class ForbiddenActionException(message: String) : DomainException(message)
