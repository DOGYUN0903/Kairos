"use client";

import Link from "next/link";
import { useStatusCounts, type CountedStatus } from "@/features/seller/dashboard/api";

const num = new Intl.NumberFormat("ko-KR");

export function StatusCards() {
  const { counts, isPending, isError, refetch } = useStatusCounts();

  if (isPending && !isError) {
    return (
      <div className="grid grid-cols-4 gap-3" aria-busy="true" aria-label="불러오는 중">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="min-h-24 rounded-lg border border-border bg-bg px-4 py-3.5">
            <div className="h-3.5 w-1/2 animate-pulse rounded bg-surface-2" />
            <div className="mt-3 h-[22px] w-1/3 animate-pulse rounded bg-surface-2" />
          </div>
        ))}
      </div>
    );
  }

  const sum = (...s: CountedStatus[]): string | null => {
    let total = 0;
    for (const k of s) {
      const v = counts[k];
      if (v === undefined) return null;
      total += v;
    }
    return num.format(total);
  };
  const cards: { label: string; status: CountedStatus; value: string | null }[] = [
    { label: "검수 대기", status: "PENDING_REVIEW", value: sum("PENDING_REVIEW") },
    { label: "판매 중", status: "ON_SALE", value: sum("ON_SALE") },
    { label: "일시 중지", status: "PAUSED", value: sum("PAUSED") },
    { label: "반려 · 종료", status: "REJECTED", value: sum("REJECTED", "ENDED") },
  ];

  return (
    <>
      <div className="grid grid-cols-4 gap-3" role="group" aria-label="상태별 상품 수">
        {cards.map((c) => (
          <Link
            key={c.label}
            href={`/seller/products?status=${c.status}`}
            className="block min-h-24 rounded-lg border border-border bg-bg px-4 py-3.5 text-foreground hover:border-navy"
          >
            <span className="text-xs text-text-2">{c.label}</span>
            <span className="mt-0.5 block text-[22px] font-bold tabular-nums">{c.value ?? "—"}</span>
            <span className="mt-1.5 block text-xs text-navy">보기 →</span>
          </Link>
        ))}
      </div>
      {isError && (
        <p role="alert" className="mt-2 text-xs text-danger">
          일부 수치를 불러오지 못했습니다.{" "}
          <button type="button" className="underline" onClick={refetch}>
            다시 시도
          </button>
        </p>
      )}
    </>
  );
}
