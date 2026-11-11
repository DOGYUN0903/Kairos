"use client";

import Link from "next/link";
import { useQueueCounts } from "@/features/admin/review/api";
import { REVIEW_TABS, type ReviewTab } from "@/features/admin/review/review-params";
import { cn } from "@/lib/utils";

const LABEL: Record<ReviewTab, string> = {
  sellers: "판매자 입점 대기",
  brands: "브랜드 대기",
  products: "상품 검수 대기",
  "change-requests": "변경 요청 대기",
};
const num = new Intl.NumberFormat("ko-KR");

export function PendingCards() {
  const { counts, isPending, isError, refetch } = useQueueCounts();

  if (isPending && !isError) {
    return (
      <div className="grid grid-cols-4 gap-3" aria-busy="true" aria-label="불러오는 중">
        {REVIEW_TABS.map((t) => (
          <div key={t} className="min-h-[104px] rounded-lg border border-border bg-bg p-4">
            <div className="h-3.5 w-3/5 animate-pulse rounded bg-surface-2" />
            <div className="mt-3 h-[26px] w-[30%] animate-pulse rounded bg-surface-2" />
          </div>
        ))}
      </div>
    );
  }
  return (
    <>
      <div className="grid grid-cols-4 gap-3" role="group" aria-label="검수 대기 건수">
        {REVIEW_TABS.map((t) => {
          const n = counts[t];
          return (
            <Link key={t} href={`/admin/review?tab=${t}`} className="block min-h-[104px] rounded-lg border border-border bg-bg p-4 text-foreground hover:border-navy">
              <span className="text-xs text-text-2">{LABEL[t]}</span>
              <span className={cn("mt-0.5 block text-[26px] font-bold tabular-nums", n === 0 && "text-text-3")}>
                {n === undefined ? "—" : num.format(n)}
                <small className="ml-0.5 text-[13px] font-medium text-text-2">건</small>
              </span>
              <span className="mt-1.5 block text-xs text-navy">검수 열기 →</span>
            </Link>
          );
        })}
      </div>
      {isError && (
        <p role="alert" className="mt-2 text-xs text-danger">
          일부 수치를 불러오지 못했습니다.{" "}
          <button type="button" className="underline" onClick={refetch}>다시 시도</button>
        </p>
      )}
    </>
  );
}
