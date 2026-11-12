"use client";

import Link from "next/link";
import { useState } from "react";
import { Money } from "@/components/common/Money";
import type { OrderSummary } from "@/features/orders/api";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { PayCountdown } from "@/features/orders/components/PayCountdown";
import { orderProgressLabel } from "@/features/orders/progress";
import { formatDate } from "@/lib/datetime";
import { cn } from "@/lib/utils";

const tone = (status: OrderSummary["status"]) => (status === "PENDING_PAYMENT" ? "chip-pending" : status === "COMPLETED" ? "chip-onsale" : status === "CANCELED" ? "chip-ended" : "chip-info");

/** 주문 내역 카드. onExpire 는 결제 대기 주문의 기한이 지났을 때(목록 재조회용) 한 번 불린다. */
export function OrderCard({ order, onExpire }: { order: OrderSummary; onExpire?: () => void }) {
  const [expired, setExpired] = useState(false);
  const pending = order.status === "PENDING_PAYMENT";
  const closed = order.status === "CANCELED";
  const detailHref = `/my/orders/${order.orderId}`;
  return (
    <article className={cn("rounded-lg border border-border", closed && "[&_.thumb]:opacity-55")}>
      <div className="flex items-center gap-3 border-b border-border px-5 py-3.5">
        <span className="text-[15px] font-bold tabular-nums">{formatDate(order.createdAt)}</span>
        <span className="text-xs tabular-nums text-text-2">주문번호 {order.orderNo}</span>
      </div>
      <div className="grid grid-cols-[88px_minmax(0,1fr)_220px_150px] items-center gap-5 p-5">
        <Link href={detailHref} aria-label={`${order.firstProductName} 주문 상세`}>
          <OrderThumb url={order.thumbnailUrl} size={88} className={cn("thumb", closed && "opacity-55")} />
        </Link>
        <div className="min-w-0">
          <div className={cn("truncate text-[15px] font-medium", closed && "opacity-55")}>
            {order.firstProductName}
            {order.itemCount > 1 && <span className="ml-1 font-normal text-text-2">외 {order.itemCount - 1}건</span>}
          </div>
          <div className="mt-1.5 text-[13px] text-text-2">
            결제 금액
            <b className="ml-1.5 text-[15px] text-foreground">
              <Money value={order.totalAmount} />
            </b>
          </div>
        </div>
        <div className="flex flex-col items-start justify-center gap-1.5 self-stretch border-l border-border pl-5">
          <span className={`chip ${tone(order.status)}`}>{orderProgressLabel(order.status, order.sellerOrderStatuses)}</span>
          {pending && <span className="text-xs text-text-2">기한이 지나면 자동 취소됩니다</span>}
          {order.status === "PAID" && order.sellerOrderStatuses.length > 1 && (
            <span className="text-xs text-text-2">판매자 {order.sellerOrderStatuses.length}곳에서 따로 배송됩니다</span>
          )}
        </div>
        <div className="flex flex-col gap-2">
          {pending && !expired ? (
            <>
              <PayCountdown
                expiresAt={order.expiresAt}
                size="sm"
                onExpire={() => {
                  setExpired(true);
                  onExpire?.();
                }}
              />
              <Link className="btn btn-primary" href={`${detailHref}/pay`}>
                결제하기
              </Link>
            </>
          ) : (
            <Link className="btn btn-ghost" href={detailHref}>
              주문 상세
            </Link>
          )}
        </div>
      </div>
    </article>
  );
}
