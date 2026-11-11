"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { SELLER_FILTERS, SELLER_FILTER_LABEL, parseSellerFilter, type SellerFilter } from "@/features/admin/sellers/actions";
import { useAdminSellers } from "@/features/admin/sellers/api";
import { SellerTable } from "@/features/admin/sellers/components/SellerTable";
import { cn } from "@/lib/utils";

/** URL(?status=&page=)이 상태의 진실이다. 전체가 기본. */
export function SellersPage() {
  const router = useRouter();
  const sp = useSearchParams();
  const status = parseSellerFilter(sp.get("status"));
  const rawPage = sp.get("page");
  const page = rawPage !== null && /^\d{1,9}$/.test(rawPage) ? Number(rawPage) : 0;
  const q = useAdminSellers({ status, page });
  const rows = q.data?.content ?? [];

  const go = (nextStatus: SellerFilter, nextPage: number) => {
    const p = new URLSearchParams();
    if (nextStatus !== "ALL") p.set("status", nextStatus);
    if (nextPage > 0) p.set("page", String(nextPage));
    const s = p.toString();
    router.push(s ? `/admin/sellers?${s}` : "/admin/sellers", { scroll: false });
  };

  return (
    <div className="flex flex-col gap-5">
      <div role="tablist" aria-label="판매자 상태" className="flex flex-wrap gap-2">
        {SELLER_FILTERS.map((f) => (
          <button
            key={f}
            type="button"
            role="tab"
            aria-selected={f === status}
            className={cn("rounded-full border px-3.5 py-1.5 text-sm", f === status ? "border-navy bg-navy font-semibold text-white" : "border-border hover:bg-surface")}
            onClick={() => go(f, 0)}
          >
            {SELLER_FILTER_LABEL[f]}
          </button>
        ))}
      </div>
      <section className="card" aria-labelledby="sellers-h">
        <h2 id="sellers-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">판매자 {q.data ? `(${q.data.totalElements})` : ""}</h2>
        {q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : q.isPending ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-12 w-full" />)}
          </div>
        ) : rows.length === 0 ? (
          <EmptyState message="해당하는 판매자가 없습니다" />
        ) : (
          <>
            <SellerTable rows={rows} />
            <div className="pb-4">
              <Pagination page={page} totalPages={q.data?.totalPages ?? 0} onChange={(p) => go(status, p)} />
            </div>
          </>
        )}
      </section>
    </div>
  );
}
