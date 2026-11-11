package com.dogyun.kairos.demo.application

/** demo 프로필 전용 계정. 운영 관리자(KAIROS_ADMIN_*)와는 별개다. */
object DemoAccounts {
    const val BUYER = "buyer@kairos.demo"
    const val SELLER = "seller@kairos.demo"
    const val SELLER_PENDING = "seller2@kairos.demo"
    const val ADMIN = "admin@kairos.demo"
}

/** 이번 시드 호출로 새로 만든 수. */
data class DemoReseedResult(
    val members: Int,
    val sellers: Int,
    val brands: Int,
    val categories: Int,
    val products: Int,
    val pendingReviews: Int,
)
