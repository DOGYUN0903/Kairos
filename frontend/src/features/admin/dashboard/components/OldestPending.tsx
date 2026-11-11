"use client";

import Link from "next/link";
import { ErrorState } from "@/components/common/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { useOldestRows } from "@/features/admin/review/api";
import { mergeOldest } from "@/features/admin/review/rows";
import { toReviewSearch } from "@/features/admin/review/review-params";
import { formatDate } from "@/lib/datetime";

const KIND = { sellers: "판매자", brands: "브랜드", products: "상품", "change-requests": "변경 요청" } as const;
const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

export const OLDEST_COUNT = 5;

export function OldestPending() {
  const { rows, isPending, isError, error, refetch } = useOldestRows();
  const oldest = mergeOldest(rows, OLDEST_COUNT);

  return (
    <section className="card" aria-labelledby="old-h">
      <div className="flex items-center justify-between border-b border-border px-5 py-4">
        <h2 id="old-h" className="text-[15px] font-bold">오래된 대기 건 {OLDEST_COUNT}개</h2>
        <span className="text-xs text-text-2">큐별 오래된 순 앞 2건</span>
      </div>
      {isError ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : isPending ? (
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
          {[0, 1, 2].map((i) => <Skeleton key={i} className="h-12 w-full" />)}
        </div>
      ) : oldest.length === 0 ? (
        <p className="px-5 py-10 text-center text-text-2">대기 중인 검수가 없습니다.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th scope="col" className={TH}>종류</th>
                <th scope="col" className={TH}>이름</th>
                <th scope="col" className={TH}>판매자</th>
                <th scope="col" className={TH}>요청일</th>
                <th scope="col" className={TH}><span className="sr-only">열기</span></th>
              </tr>
            </thead>
            <tbody>
              {oldest.map((r) => {
                const q = toReviewSearch({ tab: r.tab, id: r.id, page: 0, sort: "OLDEST" });
                return (
                  <tr key={`${r.tab}-${r.id}`}>
                    <td className={TD}><span className="chip chip-info">{KIND[r.tab]}</span></td>
                    <td className={TD}>
                      <span className="font-medium">{r.name}</span>
                      {r.meta && <span className="block text-xs text-text-3">{r.meta}</span>}
                    </td>
                    <td className={TD}>{r.sellerName ?? "-"}</td>
                    <td className={`${TD} whitespace-nowrap text-text-2`}>{formatDate(r.at)}</td>
                    <td className={TD}>
                      <Link className="btn btn-ghost btn-sm" href={`/admin/review?${q}`} aria-label={`${KIND[r.tab]} ${r.name} 열기`}>열기</Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
