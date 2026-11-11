"use client";

import Link from "next/link";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { StatusChip } from "@/components/common/StatusChip";
import { Skeleton } from "@/components/ui/skeleton";
import { useRecentProducts } from "@/features/seller/dashboard/api";
import { formatDate } from "@/lib/datetime";

export function RecentProducts() {
  const q = useRecentProducts();
  const rows = q.data?.content ?? [];

  return (
    <section className="card" aria-labelledby="recent-h">
      <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-4">
        <h2 id="recent-h" className="text-[15px] font-bold">
          최근 등록 상품
        </h2>
        <Link href="/seller/products" className="text-xs text-navy">
          전체 보기
        </Link>
      </div>
      {q.isPending ? (
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState message="아직 등록한 상품이 없습니다." action={{ label: "첫 상품 등록", href: "/seller/products/new" }} />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="bg-surface text-left text-xs font-medium text-text-2">
                <th scope="col" className="px-3.5 py-3">상품</th>
                <th scope="col" className="px-3.5 py-3">상태</th>
                <th scope="col" className="px-3.5 py-3">등록일</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((p) => (
                <tr key={p.productId} className="border-b border-border">
                  <td className="px-3.5 py-3">
                    <div className="flex items-center gap-3">
                      {p.thumbnailUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
                        <img src={p.thumbnailUrl} alt="" className="h-14 w-14 flex-none rounded object-cover" />
                      ) : (
                        <div className="grid h-14 w-14 flex-none place-items-center rounded bg-surface-2 text-[10px] text-text-3">이미지 없음</div>
                      )}
                      <div>
                        <Link href={`/seller/products/${p.productId}`} className="block font-medium hover:underline">
                          {p.name}
                        </Link>
                        <span className="text-xs text-text-2">
                          #{p.productId} · {p.brandName} · 재고 {p.stockTotal}
                        </span>
                      </div>
                    </div>
                  </td>
                  <td className="px-3.5 py-3">
                    <StatusChip kind="product" status={p.status} />
                    {p.hasPendingChangeRequest && <span className="ml-1.5 text-xs text-text-2">변경 검수 중</span>}
                  </td>
                  <td className="px-3.5 py-3 text-text-2">{formatDate(p.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
