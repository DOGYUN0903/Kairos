"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { SELLER_RETURN_PAGE_SIZE, useSellerReturnCount, useSellerReturns } from "@/features/seller/returns/api";
import { ReturnTable } from "@/features/seller/returns/components/ReturnTable";
import { RETURN_TABS, parseReturnTab, type ReturnTabStatus } from "@/features/seller/returns/tabs";
import { parsePage } from "@/lib/page";
import { cn } from "@/lib/utils";

const EMPTY_IN_TAB: Record<ReturnTabStatus, string> = {
  REQUESTED: "처리할 반품 요청이 없습니다.",
  APPROVED: "수거를 기다리는 반품이 없습니다.",
  REFUNDING: "환불을 확인 중인 반품이 없습니다.",
  COMPLETED: "완료된 반품이 없습니다.",
  REJECTED: "거절한 반품이 없습니다.",
};

const detailHref = (returnId: number) => `/seller/returns/${returnId}`;

/** URL(?status=&page=)이 탭과 페이지의 진실이다. 탭을 바꾸면 첫 페이지로 간다. */
export function SellerReturnListPage() {
  const router = useRouter();
  const sp = useSearchParams();
  const status = parseReturnTab(sp);
  const page = parsePage(sp.get("page"));
  const q = useSellerReturns({ ...(status ? { status } : {}), page });
  const pending = useSellerReturnCount();

  const go = (nextStatus: ReturnTabStatus | undefined, nextPage: number) => {
    const next = new URLSearchParams();
    if (nextStatus) next.set("status", nextStatus);
    if (nextPage > 0) next.set("page", String(nextPage));
    const qs = next.toString();
    router.push(qs ? `/seller/returns?${qs}` : "/seller/returns", { scroll: false });
  };

  const data = q.data;
  const rows = data?.content ?? [];

  return (
    <div className="flex flex-col gap-4">
      <p className="text-[13px] text-text-2">구매자가 요청한 반품입니다. 요청은 3일 안에 처리하지 않으면 자동 승인됩니다.</p>
      <div role="tablist" aria-label="반품 상태" className="flex gap-1 border-b border-border">
        {RETURN_TABS.map((t) => {
          const selected = t.status === status;
          return (
            <button
              key={t.label}
              type="button"
              role="tab"
              aria-selected={selected}
              className={cn("-mb-px flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-sm", selected ? "border-navy font-bold text-navy" : "border-transparent text-text-2 hover:text-foreground")}
              onClick={() => go(t.status, 0)}
            >
              {t.label}
              {t.status === "REQUESTED" && !pending.isError && pending.data !== undefined && pending.data > 0 && (
                <span className="rounded-full bg-danger px-1.5 text-[11px] font-bold leading-5 text-white" aria-label={`${pending.data}건`}>
                  {pending.data}
                </span>
              )}
            </button>
          );
        })}
      </div>

      <div className="card" role="tabpanel" aria-label="반품 목록">
        {q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data || q.isPlaceholderData ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 반품이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(status, 0) }} />
          ) : status ? (
            <EmptyState message={EMPTY_IN_TAB[status]} action={{ label: "전체 보기", onClick: () => go(undefined, 0) }} />
          ) : (
            <EmptyState message="받은 반품 요청이 없습니다." />
          )
        ) : (
          <>
            <ReturnTable rows={rows} detailHref={detailHref} />
            <div className="flex items-center justify-between px-3.5 pb-4">
              <span className="text-xs text-text-2">
                {data.totalElements}건 중 {data.page * SELLER_RETURN_PAGE_SIZE + 1}–{data.page * SELLER_RETURN_PAGE_SIZE + rows.length}
              </span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={(p) => go(status, p)} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
