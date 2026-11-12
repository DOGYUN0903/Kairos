"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ApiError } from "@/api/client";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { StatusChip } from "@/components/common/StatusChip";
import { useSellerSettlement } from "@/features/seller/settlements/api";
import { SettlementAdjustmentsTable } from "@/features/settlements/components/SettlementAdjustmentsTable";
import { SettlementBreakdown } from "@/features/settlements/components/SettlementBreakdown";
import { SettlementLinesTable } from "@/features/settlements/components/SettlementLinesTable";
import { formatDate, periodLastDay } from "@/lib/datetime";

export function SellerSettlementDetailPage({ settlementId }: { settlementId: number }) {
  const q = useSellerSettlement(settlementId);
  const d = q.data;

  if (q.isError && q.error instanceof ApiError && (q.error.code === "SETTLEMENT_NOT_FOUND" || q.error.status === 404)) notFound();

  return (
    <div className="flex flex-col gap-5">
      <Link href="/seller/settlements" className="inline-flex w-fit items-center gap-1 text-[13px] text-text-2 hover:underline">
        <ChevronLeft className="h-4 w-4" aria-hidden />
        정산 목록으로
      </Link>
      {q.isError && !d ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !d ? (
        <PageSkeleton />
      ) : (
        <>
          {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
          <div>
            <h2 className="flex items-center gap-2 text-lg font-bold">
              정산 {d.periodStart} ~ {periodLastDay(d.periodEnd)} <StatusChip kind="settlement" status={d.status} />
            </h2>
            <p className="mt-1.5 flex gap-4 text-[13px] text-text-2">
              <span>지급 예정일 <b className="tabular-nums text-foreground">{d.payoutDate}</b></span>
              <span>포함 주문 <b className="tabular-nums text-foreground">{d.lineCount}건</b></span>
            </p>
          </div>
          <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
            <SettlementBreakdown detail={d} />
            <aside className="flex flex-col gap-4">
              <section className="card" aria-label="정산 정보">
                <div className="border-b border-border px-5 py-4 text-[15px] font-bold">정산 정보</div>
                <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 p-5 text-sm">
                  <dt className="text-text-2">정산 기간</dt>
                  <dd className="tabular-nums">{d.periodStart} ~ {periodLastDay(d.periodEnd)}</dd>
                  <dt className="text-text-2">지급 예정일</dt>
                  <dd className="tabular-nums">{d.payoutDate}</dd>
                  <dt className="text-text-2">상태</dt>
                  <dd><StatusChip kind="settlement" status={d.status} /></dd>
                  <dt className="text-text-2">포함 주문</dt>
                  <dd className="tabular-nums">{d.lineCount}건</dd>
                  {d.status === "PAID" && (
                    <>
                      <dt className="text-text-2">지급일</dt>
                      <dd className="tabular-nums">{d.paidAt ? formatDate(d.paidAt) : "-"}</dd>
                      <dt className="text-text-2">메모</dt>
                      <dd>{d.paidMemo || "-"}</dd>
                    </>
                  )}
                </dl>
              </section>
              <p className="notice">
                <span>플랫폼이 부담한 쿠폰·포인트·무료배송은 정산에서 차감되지 않습니다</span>
              </p>
            </aside>
          </div>
          <SettlementLinesTable lines={d.lines} lineCount={d.lineCount} orderHref={(l) => `/seller/orders/${l.sellerOrderId}`} />
          <SettlementAdjustmentsTable adjustments={d.adjustments} />
        </>
      )}
    </div>
  );
}
