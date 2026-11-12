import type { ReactNode } from "react";
import { Money } from "@/components/common/Money";
import type { OrderDetail } from "@/features/orders/api";

function Row({ label, children, strong = false }: { label: string; children: ReactNode; strong?: boolean }) {
  return (
    <>
      <dt className={strong ? "font-bold text-foreground" : "text-text-2"}>{label}</dt>
      <dd className={strong ? "text-right font-bold tabular-nums" : "text-right font-medium tabular-nums"}>{children}</dd>
    </>
  );
}

/** 결제 요약. 모든 금액은 서버 응답 값 그대로이고 화면에서 더하거나 빼지 않는다. */
export function PaymentSummaryCard({ order }: { order: OrderDetail }) {
  return (
    <section aria-labelledby="pay-sum-h" className="rounded-lg border border-border bg-bg p-6">
      <h2 id="pay-sum-h" className="mb-4 text-base font-bold">
        결제 요약
      </h2>
      <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-2.5 text-sm">
        <Row label="상품 금액">
          <Money value={order.itemsAmount} />
        </Row>
        <Row label="배송비">
          <Money value={order.buyerShippingFee} />
        </Row>
        {order.productCouponDiscount !== 0 && (
          <Row label="상품 쿠폰 할인">
            −<Money value={order.productCouponDiscount} />
          </Row>
        )}
        {order.cartCouponDiscount !== 0 && (
          <Row label="장바구니 쿠폰 할인">
            −<Money value={order.cartCouponDiscount} />
          </Row>
        )}
        {order.pointsUsed !== 0 && (
          <Row label="포인트 사용">
            −<Money value={order.pointsUsed} />
          </Row>
        )}
        <Row label="결제 금액" strong>
          <Money value={order.totalAmount} />
        </Row>
        {(order.refundedAmount !== 0 || order.pointsRefunded !== 0 || order.shippingFeeRecovered !== 0) && (
          <div aria-hidden className="col-span-2 border-t border-border" />
        )}
        {order.refundedAmount !== 0 && (
          <Row label="환불 금액">
            <span className="text-danger">
              −<Money value={order.refundedAmount} />
            </span>
          </Row>
        )}
        {order.pointsRefunded !== 0 && (
          <Row label="포인트 환불">
            <Money value={order.pointsRefunded} />
          </Row>
        )}
        {order.shippingFeeRecovered > 0 && (
          <div className="col-span-2 -mt-1.5 flex justify-between pl-2.5 text-xs text-text-3">
            <span>무료배송 기준 미달로 차감된 배송비</span>
            <span className="tabular-nums">
              <Money value={order.shippingFeeRecovered} />
            </span>
          </div>
        )}
      </dl>
      <div className="mt-4 flex items-baseline justify-between border-t border-border pt-4 font-bold">
        <span>남은 결제 금액</span>
        <strong className="text-2xl tracking-tight">
          <Money value={order.remainingAmount} />
        </strong>
      </div>
    </section>
  );
}
