package com.dogyun.kairos.settlement.infrastructure

import com.dogyun.kairos.order.application.OrderQueryService
import com.dogyun.kairos.settlement.application.ConfirmedSellerOrderReader
import com.dogyun.kairos.settlement.domain.SettlementSource
import com.dogyun.kairos.settlement.domain.SettlementSourceItem
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** order 모듈의 application 서비스만 호출해 order 결과를 settlement 도메인 타입으로 번역한다(order.domain·infrastructure 미사용). */
@Component
class ConfirmedSellerOrderReaderImpl(private val orderQueryService: OrderQueryService) : ConfirmedSellerOrderReader {

    override fun findConfirmedBetween(from: LocalDateTime, to: LocalDateTime, afterId: Long, limit: Int): List<SettlementSource> =
        orderQueryService.findConfirmedForSettlement(from, to, afterId, limit).map { o ->
            SettlementSource(
                sellerOrderId = o.sellerOrderId,
                sellerId = o.sellerId,
                orderId = o.orderId,
                orderNo = o.orderNo,
                confirmedAt = o.confirmedAt,
                commissionRateBp = o.commissionRateBp,
                shippingFee = o.shippingFee,
                buyerShippingFeeRefunded = o.buyerShippingFeeRefunded,
                items = o.items.map {
                    SettlementSourceItem(
                        orderItemId = it.orderItemId,
                        ordered = it.status == "ORDERED",
                        lineAmount = it.lineAmount,
                        productCouponDiscount = it.productCouponDiscount,
                        productCouponIssuer = it.productCouponIssuer,
                        sellerBorneReturnFee = it.sellerBorneReturnFee,
                    )
                },
            )
        }
}
