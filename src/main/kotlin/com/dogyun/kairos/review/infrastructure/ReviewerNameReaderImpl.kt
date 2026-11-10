package com.dogyun.kairos.review.infrastructure

import com.dogyun.kairos.member.application.MemberQueryService
import com.dogyun.kairos.review.application.ReviewerNameReader
import org.springframework.stereotype.Component

/** 이름 첫 글자 + `**`(예 `김**`). 이메일은 쓰지 않는다. */
@Component
class ReviewerNameReaderImpl(private val memberQueryService: MemberQueryService) : ReviewerNameReader {
    override fun findMaskedNames(memberIds: Collection<Long>): Map<Long, String> =
        memberQueryService.findNames(memberIds).mapValues { (_, name) -> name.take(1) + "**" }
}
