package com.dogyun.kairos.settlement.application

import com.dogyun.kairos.settlement.domain.SettlementSource
import java.time.LocalDateTime

/** 정산 대상이 되는 확정(CONFIRMED) 판매자 주문 읽기 포트. 구현은 order.application 을 호출한다(infrastructure). 쓰기 없음. */
interface ConfirmedSellerOrderReader {
    /**
     * `seller_order_status = CONFIRMED AND confirmed_at >= from AND confirmed_at < to AND id > afterId ORDER BY id`.
     * 저장된 스냅샷 값만으로 만든 [SettlementSource]를 판매자 주문 id 오름차순으로 최대 [limit]개 준다.
     */
    fun findConfirmedBetween(from: LocalDateTime, to: LocalDateTime, afterId: Long, limit: Int): List<SettlementSource>
}
