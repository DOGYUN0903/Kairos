package com.dogyun.kairos.common.security

data class SellerPrincipal(val sellerId: Long, val memberId: Long, val canManage: Boolean, val canView: Boolean)

/** 회원 ID 로 판매자 정보를 찾는다. 구현은 seller 모듈 infrastructure 가 맡는다. */
interface SellerResolver {
    fun resolve(memberId: Long): SellerPrincipal?
}
