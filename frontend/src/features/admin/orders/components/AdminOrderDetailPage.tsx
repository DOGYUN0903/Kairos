"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { ApiError } from "@/api/client";
import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { StatusChip } from "@/components/common/StatusChip";
import { useAdminOrder } from "@/features/admin/orders/api";
import { AdminCancelDialog } from "@/features/admin/orders/components/AdminCancelDialog";
import { canCancelItem } from "@/features/orders/actions";
import type { OrderDetail, OrderItemResult } from "@/features/orders/api";
import { PaymentSummaryCard } from "@/features/orders/components/PaymentSummaryCard";
import { SellerOrderCard } from "@/features/orders/components/SellerOrderCard";
import { ShippingAddressCard } from "@/features/orders/components/ShippingAddressCard";
import { needsPolling, usePollingWindow } from "@/features/orders/polling";
import { formatDateTime } from "@/lib/datetime";

/** 관리자 주문 상세. 결제·취소 결과가 확정되지 않은 동안만 3초 간격으로 다시 읽고(최대 2분), 그 뒤에는 새로고침을 안내한다. */
export function AdminOrderDetailPage({ orderId }: { orderId: number }) {
  const [polling, setPolling] = useState(false);
  const win = usePollingWindow(polling);
  const q = useAdminOrder(orderId, { refetchInterval: win.interval });
  const data = q.data;
  const needs = data ? needsPolling(data) : false;
  useEffect(() => setPolling(needs), [needs]);

  if (q.isError && q.error instanceof ApiError && (q.error.code === "ORDER_NOT_FOUND" || q.error.status === 404)) notFound();

  return (
    <div className="flex flex-col gap-5">
      <Link href="/admin/orders" className="inline-flex w-fit items-center gap-1 text-[13px] text-text-2 hover:underline">
        <ChevronLeft className="h-4 w-4" aria-hidden />
        주문 목록
      </Link>
      {q.isError && !data ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !data ? (
        <PageSkeleton />
      ) : (
        <Detail order={data} pollExpired={win.expired} onRefresh={() => void q.refetch()} />
      )}
    </div>
  );
}

function Detail({ order, pollExpired, onRefresh }: { order: OrderDetail; pollExpired: boolean; onRefresh: () => void }) {
  const [target, setTarget] = useState<OrderItemResult | null>(null);
  return (
    <>
      <div>
        <h2 className="flex items-center gap-3 text-xl font-bold">
          <span className="tabular-nums">{order.orderNo}</span>
          <StatusChip kind="order" status={order.status} />
        </h2>
        <dl className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-[13px] text-text-2">
          <Meta label="주문 일시">{formatDateTime(order.createdAt)}</Meta>
        </dl>
      </div>

      {needsPolling(order) && (
        <div role="status" aria-live="polite" className="notice">
          <span>
            {pollExpired ? (
              <>
                결제·취소 결과가 아직 확정되지 않았습니다. 자동 확인을 멈췄습니다.{" "}
                <button type="button" className="underline" onClick={onRefresh}>
                  새로고침
                </button>
              </>
            ) : (
              "결제 또는 취소 결과를 확인하고 있습니다. 자동으로 갱신됩니다."
            )}
          </span>
        </div>
      )}

      <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6">
        <div>
          {order.sellerOrders.map((so) => (
            <SellerOrderCard
              key={so.sellerOrderId}
              sellerOrder={so}
              renderItemAction={(item) =>
                canCancelItem(order, so, item) ? (
                  <button type="button" className="btn btn-ghost btn-sm" onClick={() => setTarget(item)}>
                    취소
                  </button>
                ) : null
              }
            />
          ))}
        </div>
        <aside className="flex flex-col gap-4">
          <PaymentInfoCard order={order} />
          <PaymentSummaryCard order={order} />
          <ShippingAddressCard address={order.shippingAddress} />
        </aside>
      </div>
      <AdminCancelDialog orderId={order.orderId} item={target} onClose={() => setTarget(null)} />
    </>
  );
}

/** 문제 확인용 결제 정보. 값은 모두 서버 값이다. */
function PaymentInfoCard({ order }: { order: OrderDetail }) {
  const p = order.payment;
  return (
    <section aria-labelledby="pay-info-h" className="rounded-lg border border-border bg-bg p-6">
      <h2 id="pay-info-h" className="mb-4 text-base font-bold">
        결제 정보
      </h2>
      {p ? (
        <dl className="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-2.5 text-sm">
          <dt className="text-text-2">결제 상태</dt>
          <dd>
            <StatusChip kind="payment" status={p.status} />
          </dd>
          <dt className="text-text-2">결제 금액</dt>
          <dd className="tabular-nums">
            <Money value={p.amount} />
          </dd>
          <dt className="text-text-2">환불 금액</dt>
          <dd className="tabular-nums">
            <Money value={p.refundedAmount} />
          </dd>
          <dt className="text-text-2">paymentKey</dt>
          <dd className="break-all text-[13px] tabular-nums">{p.paymentKey ?? "—"}</dd>
          <dt className="text-text-2">결제 기한</dt>
          <dd className="tabular-nums">{formatDateTime(order.expiresAt)}</dd>
          <dt className="text-text-2">결제 일시</dt>
          <dd className="tabular-nums">{order.paidAt ? formatDateTime(order.paidAt) : "—"}</dd>
        </dl>
      ) : (
        <dl className="grid grid-cols-[88px_minmax(0,1fr)] gap-x-3 gap-y-2.5 text-sm">
          <dt className="text-text-2">결제</dt>
          <dd className="text-text-2">결제 내역이 없습니다.</dd>
          <dt className="text-text-2">결제 기한</dt>
          <dd className="tabular-nums">{formatDateTime(order.expiresAt)}</dd>
        </dl>
      )}
    </section>
  );
}

function Meta({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-1.5">
      <dt>{label}</dt>
      <dd className="font-medium tabular-nums text-foreground">{children}</dd>
    </div>
  );
}
