package com.dogyun.kairos.deal.domain

import com.dogyun.kairos.common.domain.PageResult
import java.time.LocalDateTime

interface DealRepository {
    fun save(deal: Deal): Deal
    fun findById(id: DealId): Deal?

    /** status 가 null 이면 전체. 상태는 저장값이 아니라 now 기준으로 계산해 거른다. 최신 등록순. */
    fun findPage(status: DealStatus?, now: LocalDateTime, page: Int, size: Int): PageResult<Deal>

    /** 소프트 삭제. */
    fun delete(id: DealId)
}
