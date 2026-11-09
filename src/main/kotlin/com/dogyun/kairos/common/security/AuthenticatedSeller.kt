package com.dogyun.kairos.common.security

/**
 * 컨트롤러 파라미터(타입 [SellerPrincipal])에 붙이면 로그인 회원의 판매자 정보를 주입한다.
 * [requireManage] 가 true 면 APPROVED 만, false 면 APPROVED·SUSPENDED 를 통과시킨다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthenticatedSeller(val requireManage: Boolean = true)
