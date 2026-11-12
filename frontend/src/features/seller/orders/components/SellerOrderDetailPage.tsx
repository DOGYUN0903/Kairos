"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useRef, useState } from "react";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { StatusChip } from "@/components/common/StatusChip";
import type { OrderItemResult } from "@/features/orders/api";
import { carrierName } from "@/features/orders/carriers";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { ShippingAddressCard } from "@/features/orders/components/ShippingAddressCard";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { useSellerCapabilities } from "@/features/seller/gate/useSellerCapabilities";
import { canDeliver, canSellerCancelItem, canShip } from "@/features/seller/orders/actions";
import { useDeliver, useSellerOrder, type SellerOrderDetail } from "@/features/seller/orders/api";
import { SellerCancelDialog } from "@/features/seller/orders/components/SellerCancelDialog";
import { SellerStatusChip } from "@/features/seller/orders/components/SellerStatusChip";
import { ShipDialog } from "@/features/seller/orders/components/ShipDialog";
import { toast } from "@/hooks/use-toast";
import { formatDateTime } from "@/lib/datetime";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";
const ACTOR: Record<string, string> = { BUYER: "구매자 취소", SELLER: "판매자 취소", ADMIN: "관리자 취소", SYSTEM: "자동 취소" };
const SUSPENDED_TITLE = "정지 중";

export function SellerOrderDetailPage({ sellerOrderId }: { sellerOrderId: number }) {
  const q = useSellerOrder(sellerOrderId);
  if (q.isError && q.error instanceof ApiError && (q.error.code === "SELLER_ORDER_NOT_FOUND" || q.error.status === 404)) notFound();

  return (
    <div className="flex flex-col gap-5">
      <Link href="/seller/orders" className="inline-flex w-fit items-center gap-1 text-[13px] text-text-2 hover:underline">
        <ChevronLeft className="h-4 w-4" aria-hidden />
        주문 목록
      </Link>
      {q.isError && !q.data ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !q.data ? (
        <PageSkeleton />
      ) : (
        <Detail so={q.data} />
      )}
    </div>
  );
}

function Detail({ so }: { so: SellerOrderDetail }) {
  const { canProcessOrders } = useSellerCapabilities();
  const setBanner = useSellerBanner();
  const qc = useQueryClient();
  const deliver = useDeliver(so.sellerOrderId);
  const [shipOpen, setShipOpen] = useState(false);
  const [deliverOpen, setDeliverOpen] = useState(false);
  const [delivering, setDelivering] = useState(false);
  const deliverRef = useRef(false);
  const [cancelTarget, setCancelTarget] = useState<OrderItemResult | null>(null);

  const carrier = carrierName(so.carrierCode);
  const itemCount = so.items.filter((i) => i.status === "ORDERED").length;

  const confirmDeliver = async () => {
    if (deliverRef.current) return;
    deliverRef.current = true;
    setDelivering(true);
    try {
      await deliver();
      toast({ title: "배송 완료로 처리했습니다" });
      setDeliverOpen(false);
    } catch (e) {
      if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
        setBanner("판매자 상태 때문에 처리할 수 없습니다.");
        void qc.invalidateQueries({ queryKey: queryKeys.seller.me });
      } else {
        toast({ title: resolveError(e).message, variant: "destructive" });
      }
      setDeliverOpen(false);
    } finally {
      deliverRef.current = false;
      setDelivering(false);
    }
  };

  const shipBlocked = so.items.some((i) => i.status === "CANCEL_REQUESTED");
  const shipTitle = !canProcessOrders ? SUSPENDED_TITLE : shipBlocked ? "취소를 확인 중인 항목이 있어 발송할 수 없습니다" : undefined;

  return (
    <>
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="flex items-center gap-3 text-xl font-bold">
            <span className="tabular-nums">{so.orderNo}</span>
            <SellerStatusChip status={so.status} />
          </h2>
          <dl className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-[13px] text-text-2">
            <Meta label="주문 일시">{formatDateTime(so.orderedAt)}</Meta>
            {carrier && <Meta label="택배사">{carrier}</Meta>}
            {so.trackingNumber && <Meta label="송장번호">{so.trackingNumber}</Meta>}
          </dl>
        </div>
        {so.status === "PAID" && (
          <button
            type="button"
            className="btn btn-primary"
            disabled={!canProcessOrders || !canShip(so)}
            title={shipTitle}
            onClick={() => setShipOpen(true)}
          >
            발송 처리
          </button>
        )}
        {canDeliver(so) && (
          <button
            type="button"
            className="btn btn-primary"
            disabled={!canProcessOrders || delivering}
            title={!canProcessOrders ? SUSPENDED_TITLE : undefined}
            onClick={() => setDeliverOpen(true)}
          >
            배송 완료
          </button>
        )}
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6">
        <section className="card" aria-labelledby="so-items-h">
          <div className="flex items-center justify-between border-b border-border px-5 py-4">
            <h2 id="so-items-h" className="text-[15px] font-bold">주문 항목</h2>
            <span className="text-xs text-text-2">{so.items.length}개</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr>
                  <th scope="col" className={TH}>상품</th>
                  <th scope="col" className={`${TH} text-right`}>수량</th>
                  <th scope="col" className={`${TH} text-right`}>금액</th>
                  <th scope="col" className={TH}>상태</th>
                  {so.status === "PAID" && <th scope="col" className={`${TH} text-right`}>작업</th>}
                </tr>
              </thead>
              <tbody>
                {so.items.map((item) => {
                  const canceled = item.status === "CANCELED";
                  return (
                    <tr key={item.orderItemId}>
                      <td className={TD}>
                        <div className={cn("flex items-center gap-3", canceled && "opacity-55")}>
                          <OrderThumb url={item.thumbnailUrl} size={44} />
                          <div className="min-w-0">
                            <span className="block truncate font-medium">{item.productName}</span>
                            <span className="text-xs text-text-2">
                              {item.brandName} · {item.optionDisplayName}
                            </span>
                          </div>
                        </div>
                      </td>
                      <td className={`${TD} text-right tabular-nums ${canceled ? "opacity-55" : ""}`}>{item.quantity}</td>
                      <td className={`${TD} text-right font-bold ${canceled ? "opacity-55" : ""}`}>
                        <Money value={item.lineAmount} />
                      </td>
                      <td className={TD}>
                        <StatusChip kind="orderItem" status={item.status} />
                        {item.status === "CANCEL_REQUESTED" && <div className="mt-1 text-xs text-text-2">취소를 확인하고 있습니다</div>}
                        {canceled && (
                          <div className="mt-1 text-xs text-text-2">
                            {item.cancelActor ? `${ACTOR[item.cancelActor] ?? "취소"} · ` : ""}환불 <Money value={item.refundAmount} />
                          </div>
                        )}
                      </td>
                      {so.status === "PAID" && (
                        <td className={`${TD} text-right`}>
                          {canSellerCancelItem(so, item) && (
                            <button
                              type="button"
                              className="btn btn-danger btn-sm"
                              disabled={!canProcessOrders}
                              title={!canProcessOrders ? SUSPENDED_TITLE : undefined}
                              onClick={() => setCancelTarget(item)}
                            >
                              항목 취소
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-end gap-5 px-5 py-3.5 text-[13px] text-text-2">
            <span>
              배송비<b className="ml-1.5 text-foreground"><Money value={so.shippingFee} /></b>
            </span>
            <span aria-hidden className="h-3 w-px bg-border" />
            <span>
              부담 주체<b className="ml-1.5 text-foreground">{so.shippingFeePayer === "PLATFORM" ? "KAIROS (무료배송)" : "구매자"}</b>
            </span>
          </div>
        </section>

        <aside className="flex flex-col gap-4">
          <ShippingAddressCard address={so.shippingAddress} />
          <section className="rounded-lg border border-border bg-bg p-6" aria-labelledby="so-timeline-h">
            <h2 id="so-timeline-h" className="mb-4 text-base font-bold">진행 시각</h2>
            <ol className="flex flex-col gap-2.5 text-sm">
              <Step label="주문" at={so.orderedAt} />
              <Step label="발송" at={so.shippedAt} />
              <Step label="배송 완료" at={so.deliveredAt} />
              <Step label="구매 확정" at={so.confirmedAt} />
            </ol>
          </section>
        </aside>
      </div>

      <ShipDialog sellerOrderId={so.sellerOrderId} orderNo={so.orderNo} itemCount={itemCount} open={shipOpen} onClose={() => setShipOpen(false)} />
      <ConfirmDialog
        open={deliverOpen}
        title="배송 완료로 처리할까요?"
        description="구매자에게 배송 완료로 표시되며, 되돌릴 수 없습니다."
        confirmLabel={delivering ? "처리 중…" : "배송 완료"}
        onCancel={() => !delivering && setDeliverOpen(false)}
        onConfirm={() => void confirmDeliver()}
      />
      <SellerCancelDialog sellerOrderId={so.sellerOrderId} item={cancelTarget} onClose={() => setCancelTarget(null)} />
    </>
  );
}

function Meta({ label, children }: { label: string; children: string }) {
  return (
    <div className="flex gap-1.5">
      <dt>{label}</dt>
      <dd className="font-medium tabular-nums text-foreground">{children}</dd>
    </div>
  );
}

function Step({ label, at }: { label: string; at: string | null | undefined }) {
  return (
    <li className={cn("flex justify-between", at ? "font-medium text-foreground" : "text-text-3")}>
      <span>{label}</span>
      <span className="tabular-nums">{at ? formatDateTime(at) : "—"}</span>
    </li>
  );
}
