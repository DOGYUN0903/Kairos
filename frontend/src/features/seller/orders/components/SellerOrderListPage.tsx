"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { useSellerOrderCount, useSellerOrders, SELLER_ORDER_PAGE_SIZE, type SellerOrderStatus } from "@/features/seller/orders/api";
import { SellerOrderTable } from "@/features/seller/orders/components/SellerOrderTable";
import { SELLER_ORDER_TABS, parseSellerOrderParams } from "@/features/seller/orders/tabs";
import { cn } from "@/lib/utils";

const EMPTY_IN_TAB: Partial<Record<SellerOrderStatus, string>> = {
  PAID: "발송 대기 중인 주문이 없습니다.",
  SHIPPING: "배송 중인 주문이 없습니다.",
  DELIVERED: "배송 완료된 주문이 없습니다.",
  CONFIRMED: "구매 확정된 주문이 없습니다.",
  CANCELED: "취소된 주문이 없습니다.",
};

/** URL(?status=&page=)이 탭과 페이지의 진실이다. 탭을 바꾸면 첫 페이지로 간다. */
export function SellerOrderListPage() {
  const router = useRouter();
  const { status, page } = parseSellerOrderParams(useSearchParams());
  const q = useSellerOrders({ status, page });
  const pending = useSellerOrderCount("PAID");

  const go = (nextStatus: SellerOrderStatus | undefined, nextPage: number) => {
    const sp = new URLSearchParams();
    if (nextStatus) sp.set("status", nextStatus);
    if (nextPage > 0) sp.set("page", String(nextPage));
    const qs = sp.toString();
    router.push(qs ? `/seller/orders?${qs}` : "/seller/orders", { scroll: false });
  };

  const data = q.data;
  const rows = data?.content ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div role="tablist" aria-label="주문 상태" className="flex gap-1 border-b border-border">
        {SELLER_ORDER_TABS.map((t) => {
          const selected = t.status === status;
          return (
            <button
              key={t.label}
              type="button"
              role="tab"
              aria-selected={selected}
              className={cn(
                "-mb-px flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-sm",
                selected ? "border-navy font-bold text-navy" : "border-transparent text-text-2 hover:text-foreground",
              )}
              onClick={() => go(t.status, 0)}
            >
              {t.label}
              {t.status === "PAID" && pending.data !== undefined && pending.data > 0 && (
                <span className="rounded-full bg-danger px-1.5 text-[11px] font-bold leading-5 text-white" aria-label={`${pending.data}건`}>
                  {pending.data}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className="card" role="tabpanel" aria-label="주문 목록">
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 주문이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(status, 0) }} />
          ) : status ? (
            <EmptyState message={EMPTY_IN_TAB[status] ?? "해당하는 주문이 없습니다."} action={{ label: "전체 보기", onClick: () => go(undefined, 0) }} />
          ) : (
            <EmptyState message="받은 주문이 없습니다." />
          )
        ) : (
          <>
            <SellerOrderTable rows={rows} />
            <div className="flex items-center justify-between px-3.5 pb-4">
              <span className="text-xs text-text-2">
                {data.totalElements}건 중 {data.page * SELLER_ORDER_PAGE_SIZE + 1}–{data.page * SELLER_ORDER_PAGE_SIZE + rows.length}
              </span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go(status, p)} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
