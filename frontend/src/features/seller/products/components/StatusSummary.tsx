"use client";

import type { ProductStatus } from "@/features/seller/products/api";
import { cn } from "@/lib/utils";

/** 스펙 §9-2 의 5칸. 반려·종료는 API 가 상태 하나만 받으므로 "반려" 칸으로 두고, 종료는 상태 선택으로 고른다. */
const CELLS: { status: ProductStatus | undefined; label: string }[] = [
  { status: undefined, label: "전체" },
  { status: "PENDING_REVIEW", label: "검수 대기" },
  { status: "ON_SALE", label: "판매 중" },
  { status: "PAUSED", label: "일시 중지" },
  { status: "REJECTED", label: "반려" },
];

/** 집계 API 가 없어 선택한 칸에만 현재 필터의 totalElements 를 보인다(스펙 §9-2). */
export function StatusSummary({
  status,
  total,
  onSelect,
}: {
  status: ProductStatus | undefined;
  total: number | undefined;
  onSelect: (status: ProductStatus | undefined) => void;
}) {
  return (
    <div role="group" aria-label="상태별 상품 수" className="mb-4 grid grid-cols-5 overflow-hidden rounded-lg border border-border">
      {CELLS.map((c) => {
        const pressed = c.status === status;
        return (
          <button
            key={c.label}
            type="button"
            aria-pressed={pressed}
            onClick={() => onSelect(c.status)}
            className={cn(
              "flex min-h-[72px] flex-col items-start justify-center gap-1 border-r border-border px-4 py-3 text-left last:border-r-0",
              pressed ? "bg-navy text-white" : "bg-bg hover:bg-surface",
            )}
          >
            <span className={cn("text-xs", pressed ? "text-white/80" : "text-text-2")}>{c.label}</span>
            {pressed && <span className="text-xl font-bold tabular-nums">{total ?? "–"}</span>}
          </button>
        );
      })}
    </div>
  );
}
