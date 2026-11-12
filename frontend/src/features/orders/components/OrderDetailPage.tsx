"use client";

import Link from "next/link";
import { notFound } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useNow } from "@/hooks/useNow";
import { ApiError } from "@/api/client";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { canCancelItem, canConfirmPurchase } from "@/features/orders/actions";
import { useOrder, type OrderItemResult } from "@/features/orders/api";
import { CancelItemDialog } from "@/features/orders/components/CancelItemDialog";
import { ConfirmPurchaseButton } from "@/features/orders/components/ConfirmPurchaseButton";
import { OrderStatusHeader } from "@/features/orders/components/OrderStatusHeader";
import { PaymentSummaryCard } from "@/features/orders/components/PaymentSummaryCard";
import { SellerOrderCard } from "@/features/orders/components/SellerOrderCard";
import { ShippingAddressCard } from "@/features/orders/components/ShippingAddressCard";
import { expiredRecheckInterval, isPastExpiry, shouldRefetchAtExpiry } from "@/features/orders/expiry";
import { secondsUntilKst } from "@/lib/datetime";
import { needsPolling, usePollingWindow } from "@/features/orders/polling";

/** 구매자 주문 상세. 결과가 확정되지 않은 동안만 3초 간격으로 다시 읽고(최대 2분), 그 뒤에는 새로고침을 안내한다. */
export function OrderDetailPage({ orderId }: { orderId: number }) {
  const [polling, setPolling] = useState(false);
  const win = usePollingWindow(polling);
  // 시계는 이 페이지 하나뿐이다(헤더의 카운트다운도 이 값으로 그린다).
  const now = useNow();
  const [expiryInterval, setExpiryInterval] = useState<number | false>(false);
  const q = useOrder(orderId, { refetchInterval: win.interval || expiryInterval });
  const wasPast = useRef(false);
  const pastSince = useRef<number | null>(null);
  const [target, setTarget] = useState<OrderItemResult | null>(null);

  const data = q.data;
  const needs = data ? needsPolling(data) : false;
  useEffect(() => setPolling(needs), [needs]);

  // 결제 대기 주문이 기한을 넘는 순간 한 번 다시 읽고, 서버가 취소할 때까지 10초마다(최대 2분) 다시 확인한다.
  const isPast = data ? isPastExpiry(data.status, secondsUntilKst(data.expiresAt, now)) : false;
  const refetch = q.refetch;
  useEffect(() => {
    if (shouldRefetchAtExpiry(wasPast.current, isPast)) {
      pastSince.current = Date.now();
      void refetch();
    }
    if (!isPast) pastSince.current = null;
    wasPast.current = isPast;
  }, [isPast, refetch]);
  const nextInterval = expiredRecheckInterval(isPast, pastSince.current === null ? 0 : now - pastSince.current);
  useEffect(() => setExpiryInterval(nextInterval), [nextInterval]);

  if (q.isError && q.error instanceof ApiError && (q.error.code === "ORDER_NOT_FOUND" || q.error.status === 404)) notFound();

  return (
    <div>
      <nav aria-label="현재 위치" className="mb-3 flex gap-2 text-[13px] text-text-2">
        <Link href="/my/orders" className="hover:underline">
          주문 내역
        </Link>
        <span aria-hidden>/</span>
        <span>주문 상세</span>
      </nav>
      <h1 className="mb-6 text-[26px] font-bold tracking-tight">주문 상세</h1>
      {q.isError && !data ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : (
        <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-8">
          <div>
            <OrderStatusHeader
              order={data}
              nowMs={now}
              pollExpired={win.expired}
              onRefresh={() => void q.refetch()}
            />
            <div className="mt-4">
              {data.sellerOrders.map((so) => (
                <SellerOrderCard
                  key={so.sellerOrderId}
                  sellerOrder={so}
                  shipAction={canConfirmPurchase(so) ? <ConfirmPurchaseButton orderId={orderId} sellerOrderId={so.sellerOrderId} /> : undefined}
                  renderItemAction={(item) =>
                    canCancelItem(data, so, item) ? (
                      <button type="button" className="btn btn-ghost btn-sm" onClick={() => setTarget(item)}>
                        취소
                      </button>
                    ) : null
                  }
                />
              ))}
            </div>
            <p className="mt-4 text-[13px] text-text-2">
              항목 취소는 판매자가 발송하기 전(배송 준비 중)에만 할 수 있습니다. 구매 확정은 배송 완료 후에 할 수 있으며, 확정하면 되돌릴 수 없습니다.
            </p>
          </div>
          <aside className="flex flex-col gap-4">
            <PaymentSummaryCard order={data} />
            <ShippingAddressCard address={data.shippingAddress} />
          </aside>
          <CancelItemDialog orderId={orderId} item={target} hasCartCoupon={data.cartCouponId != null} onClose={() => setTarget(null)} />
        </div>
      )}
    </div>
  );
}
