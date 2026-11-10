package com.dogyun.kairos.review.application

/** member 의 회원 이름 조회 포트. 이름은 마스킹해서 돌려준다(첫 글자 + `**`). 없는 회원은 결과에서 빠진다. */
interface ReviewerNameReader {
    fun findMaskedNames(memberIds: Collection<Long>): Map<Long, String>
}
