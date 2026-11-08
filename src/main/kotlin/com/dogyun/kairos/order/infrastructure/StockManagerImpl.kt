package com.dogyun.kairos.order.infrastructure

import com.dogyun.kairos.deal.application.StockService
import com.dogyun.kairos.order.application.StockManager
import org.springframework.stereotype.Component

/** deal 모듈의 application 서비스를 호출한다. 주문 트랜잭션에 참여한다. */
@Component
class StockManagerImpl(private val stockService: StockService) : StockManager {
    override fun decrease(dealId: Long, quantity: Int) = stockService.decrease(dealId, quantity)
    override fun restore(dealId: Long, quantity: Int) = stockService.restore(dealId, quantity)
}
