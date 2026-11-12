"use client";

import { ErrorState } from "@/components/common/ErrorState";
import { usePointSummary } from "@/features/points/api";
import { formatPoints } from "@/lib/money";

/** 보유 포인트, 30일 안 만료 예정(0이면 숨김), 사용 안내. */
export function PointSummaryCard() {
  const q = usePointSummary();
  if (q.isError) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />;
  if (!q.data) return <div className="mt-6 h-28 animate-pulse rounded border border-border bg-surface" aria-hidden />;
  const { balance, expiringWithin30Days } = q.data;
  return (
    <div className="mt-6 flex flex-wrap items-center justify-between gap-4 rounded border border-border bg-surface px-6 py-5">
      <div>
        <div className="text-[13px] text-text-2">보유 포인트</div>
        <strong className="text-[28px] font-bold tracking-tight text-navy">{formatPoints(balance)}</strong>
      </div>
      <div className="flex flex-col items-start gap-2 sm:items-end">
        {expiringWithin30Days > 0 && (
          <span className="chip chip-pending">30일 안에 만료 예정 {formatPoints(expiringWithin30Days)}</span>
        )}
        <div className="text-xs text-text-2">1,000P 이상 보유 시 10P 단위로, 상품 금액의 30%까지 사용</div>
      </div>
    </div>
  );
}
