package com.dogyun.kairos.benefit.domain

import com.dogyun.kairos.common.domain.DomainException
import com.dogyun.kairos.common.domain.ErrorCode

/** 회원 포인트 잔액. 잔액을 바꾸는 쪽은 [PointRepository.lockOrCreate] 로 행을 먼저 잠근다. */
class MemberPoint(
    val id: Long?,
    val memberId: Long,
    balance: Long,
) {
    var balance: Long = balance
        private set

    fun add(amount: Long) {
        require(amount >= 0) { "적립액은 0 이상이어야 한다." }
        balance += amount
    }

    /** 결과가 음수가 되면 `POINT_INSUFFICIENT`. */
    fun subtract(amount: Long) {
        require(amount >= 0) { "차감액은 0 이상이어야 한다." }
        if (balance - amount < 0) throw DomainException(ErrorCode.POINT_INSUFFICIENT)
        balance -= amount
    }
}
