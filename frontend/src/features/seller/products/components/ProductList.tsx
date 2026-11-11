"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { useSellerCapabilities } from "@/features/seller/gate/useSellerCapabilities";
import { useSellerProducts, type ProductStatus } from "@/features/seller/products/api";
import { ProductTable } from "@/features/seller/products/components/ProductTable";
import { StatusSummary } from "@/features/seller/products/components/StatusSummary";

const STATUS_OPTIONS: { value: ProductStatus; label: string }[] = [
  { value: "PENDING_REVIEW", label: "검수 대기" },
  { value: "ON_SALE", label: "판매 중" },
  { value: "PAUSED", label: "일시 중지" },
  { value: "REJECTED", label: "반려" },
  { value: "ENDED", label: "판매 종료" },
];

function parseStatus(v: string | null): ProductStatus | undefined {
  return STATUS_OPTIONS.find((o) => o.value === v)?.value;
}

function parsePage(v: string | null): number {
  const n = Number(v ?? "0");
  return Number.isSafeInteger(n) && n >= 0 ? n : 0;
}

/** URL(?status=&page=)이 필터의 진실이다. */
export function ProductList() {
  const router = useRouter();
  const params = useSearchParams();
  const status = parseStatus(params.get("status"));
  const page = parsePage(params.get("page"));
  const { canManage } = useSellerCapabilities();
  const q = useSellerProducts({ status, page });

  const go = (next: { status: ProductStatus | undefined; page: number }) => {
    const sp = new URLSearchParams();
    if (next.status) sp.set("status", next.status);
    if (next.page > 0) sp.set("page", String(next.page));
    const qs = sp.toString();
    router.replace(qs ? `/seller/products?${qs}` : "/seller/products");
  };

  const rows = q.data?.content ?? [];

  return (
    <>
      <StatusSummary status={status} total={q.data?.totalElements} onSelect={(s) => go({ status: s, page: 0 })} />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <label htmlFor="statusFilter" className="sr-only">
          상태
        </label>
        <select
          id="statusFilter"
          className="input w-auto"
          value={status ?? ""}
          onChange={(e) => go({ status: parseStatus(e.target.value), page: 0 })}
        >
          <option value="">모든 상태</option>
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <span className="flex-1" />
        {canManage ? (
          <Link className="btn btn-primary" href="/seller/products/new">
            + 상품 등록
          </Link>
        ) : (
          <button type="button" className="btn btn-primary" disabled title="정지 중">
            + 상품 등록
          </button>
        )}
      </div>

      <div className="card">
        {q.isPending ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-4">
            {[0, 1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : rows.length === 0 ? (
          status ? (
            <EmptyState message="해당 상태의 상품이 없습니다." action={{ label: "전체 보기", onClick: () => go({ status: undefined, page: 0 }) }} />
          ) : (
            <EmptyState message="등록한 상품이 없습니다." {...(canManage ? { action: { label: "상품 등록", href: "/seller/products/new" } } : {})} />
          )
        ) : (
          <>
            <ProductTable rows={rows} canManage={canManage} />
            <div className="flex items-center justify-between px-3.5 pb-4">
              <span className="text-xs text-text-2">총 {q.data.totalElements}개</span>
              <Pagination page={q.data.page} totalPages={q.data.totalPages} onChange={(p) => go({ status, page: p })} />
            </div>
          </>
        )}
      </div>
      <p className="hint mt-4">
        판매가·재고·SKU 판매 여부는 바로 반영됩니다. 상품명, 설명, 이미지, 카테고리, 옵션 구성 변경은 변경 요청으로 저장되어 재검수를 받으며, 상품당 하나만 대기할 수 있습니다.
      </p>
    </>
  );
}
