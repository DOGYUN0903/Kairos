"use client";

import { ChevronLeft } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useRef, useState } from "react";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { StatusChip } from "@/components/common/StatusChip";
import { useAdminSettlement, useAdminSettlementActions } from "@/features/admin/settlements/api";
import { PayDialog } from "@/features/admin/settlements/components/PayDialog";
import { SettlementAdjustmentsTable } from "@/features/settlements/components/SettlementAdjustmentsTable";
import { SettlementBreakdown } from "@/features/settlements/components/SettlementBreakdown";
import { SettlementLinesTable } from "@/features/settlements/components/SettlementLinesTable";
import { toast } from "@/hooks/use-toast";
import { formatDate, periodLastDay } from "@/lib/datetime";
import { formatKrw } from "@/lib/money";

export function AdminSettlementDetailPage({ settlementId }: { settlementId: number }) {
  const q = useAdminSettlement(settlementId);
  const { confirm, pay } = useAdminSettlementActions(settlementId);
  const [dialog, setDialog] = useState<"confirm" | "pay" | null>(null);
  const [busy, setBusy] = useState(false);
  const inFlight = useRef(false);
  const d = q.data;

  if (q.isError && q.error instanceof ApiError && (q.error.code === "SETTLEMENT_NOT_FOUND" || q.error.status === 404)) notFound();

  // 확정·지급 완료는 키 없는 멱등 PATCH 다. 처리 중에는 다시 보내지 않는다(ref 는 state 반영 전의 연타도 막는다).
  const run = async (fn: () => Promise<void>, doneTitle: string) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await fn();
      toast({ title: doneTitle });
    } catch (e) {
      // 409 SETTLEMENT_INVALID_STATE_TRANSITION 포함: 서버 메시지를 보이고, 상세는 api 훅이 이미 다시 읽는다.
      toast({ title: resolveError(e).message, variant: "destructive" });
    } finally {
      inFlight.current = false;
      setBusy(false);
      setDialog(null);
    }
  };

  return (
    <div className="flex flex-col gap-5">
      <Link href="/admin/settlements" className="inline-flex w-fit items-center gap-1 text-[13px] text-text-2 hover:underline">
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
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="flex items-center gap-2 text-lg font-bold">
                정산 {d.periodStart} ~ {periodLastDay(d.periodEnd)} <StatusChip kind="settlement" status={d.status} />
              </h2>
              <p className="mt-1.5 flex gap-4 text-[13px] text-text-2">
                <span>지급 예정일 <b className="tabular-nums text-foreground">{d.payoutDate}</b></span>
                <span>포함 주문 <b className="tabular-nums text-foreground">{d.lineCount}건</b></span>
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Link href={`/admin/settlements/adjustments?sellerId=${d.sellerId}`} className="btn btn-ghost">
                이 판매자의 조정 보기
              </Link>
              {d.status === "GENERATED" && (
                <button type="button" className="btn btn-primary" disabled={busy} onClick={() => setDialog("confirm")}>
                  정산 확정
                </button>
              )}
              {d.status === "CONFIRMED" && (
                <button type="button" className="btn btn-primary" disabled={busy} onClick={() => setDialog("pay")}>
                  지급 완료
                </button>
              )}
            </div>
          </div>
          <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
            <SettlementBreakdown detail={d} />
            <aside className="flex flex-col gap-4">
              <section className="card" aria-label="정산 정보">
                <div className="border-b border-border px-5 py-4 text-[15px] font-bold">정산 정보</div>
                <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 p-5 text-sm">
                  <dt className="text-text-2">판매자</dt>
                  <dd>
                    {d.sellerName} · <StatusChip kind="seller" status={d.sellerStatus} />
                  </dd>
                  <dt className="text-text-2">정산 기간</dt>
                  <dd className="tabular-nums">{d.periodStart} ~ {periodLastDay(d.periodEnd)}</dd>
                  <dt className="text-text-2">지급 예정일</dt>
                  <dd className="tabular-nums">{d.payoutDate}</dd>
                  <dt className="text-text-2">상태</dt>
                  <dd><StatusChip kind="settlement" status={d.status} /></dd>
                  <dt className="text-text-2">포함 주문</dt>
                  <dd className="tabular-nums">{d.lineCount}건</dd>
                  {d.confirmedAt && (
                    <>
                      <dt className="text-text-2">확정일</dt>
                      <dd className="tabular-nums">{formatDate(d.confirmedAt)}</dd>
                    </>
                  )}
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
          <SettlementLinesTable lines={d.lines} orderHref={(l) => `/admin/orders/${l.orderId}`} />
          <SettlementAdjustmentsTable adjustments={d.adjustments} />

          <ConfirmDialog
            open={dialog === "confirm"}
            title="정산 확정"
            description={`${d.sellerName} · ${d.periodStart} ~ ${periodLastDay(d.periodEnd)} (지급액 ${formatKrw(d.payableAmount)}) 정산을 확정할까요? 확정한 뒤에는 지급 완료로 넘어갈 수 있습니다.`}
            confirmLabel="확정"
            onConfirm={() => void run(confirm, "정산을 확정했습니다")}
            onCancel={() => !busy && setDialog(null)}
          />
          {dialog === "pay" && (
            <PayDialog
              summary={`${d.sellerName} · ${d.periodStart} ~ ${periodLastDay(d.periodEnd)}`}
              totalAmount={d.totalAmount}
              payableAmount={d.payableAmount}
              busy={busy}
              onSubmit={(memo) => void run(() => pay(memo || undefined), "지급 완료로 표시했습니다")}
              onCancel={() => setDialog(null)}
            />
          )}
        </>
      )}
    </div>
  );
}
