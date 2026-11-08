package com.dogyun.kairos.common.domain

open class DomainException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
