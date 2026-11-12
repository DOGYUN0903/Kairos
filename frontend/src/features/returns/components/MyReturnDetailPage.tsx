"use client";

import Link from "next/link";
import { notFound } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { ApiError } from "@/api/client";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { StatusChip } from "@/components/common/StatusChip";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { usePollingWindow } from "@/features/orders/polling";
import { MyPageHead } from "@/features/mypage/components/MyPageHead";
import { useMyReturn } from "@/features/returns/api";
import { RefundBreakdown } from "@/features/returns/components/RefundBreakdown";
import { ReturnSteps } from "@/features/returns/components/ReturnSteps";
import { WithdrawReturnControl } from "@/features/returns/components/WithdrawReturnControl";
import { canWithdraw, refundRows } from "@/features/returns/return-actions";
import { needsReturnPolling, returnReasonLabel } from "@/features/returns/return-text";
import { joinDot } from "@/lib/join-dot";

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-border">
      <h2 className="border-b border-border px-5 py-3 text-[15px] font-bold">{title}</h2>
      <div className="p-5">{children}</div>
    </section>
  );
}

/** 반품 상세. 환불 확인 중인 동안만 3초 간격으로 다시 읽는다(최대 2분, 그 뒤에는 새로고침 안내). */
export function MyReturnDetailPage({ returnId }: { returnId: number }) {
  const [polling, setPolling] = useState(false);
  const win = usePollingWindow(polling);
  const q = useMyReturn(returnId, { refetchInterval: win.interval });
  const r = q.data;
  const needs = r ? needsReturnPolling(r.status) : false;
  useEffect(() => setPolling(needs), [needs]);

  if (q.isError && q.error instanceof ApiError && (q.error.code === "RETURN_NOT_FOUND" || q.error.status === 404)) notFound();

  return (
    <div>
      <MyPageHead
        title="반품 상세"
        aside={
          <Link href="/my/returns" className="text-[13px] text-text-2 hover:underline">
            반품 내역으로
          </Link>
        }
      />
      {q.isError && !r ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : !r ? (
        <PageSkeleton />
      ) : (
        <div className="mt-5 grid gap-4">
          <section className="flex items-center gap-4 rounded-lg border border-border p-5">
            <OrderThumb url={r.thumbnailUrl} size={72} />
            <div className="min-w-0 flex-1">
              <div className="text-xs text-text-2">{r.sellerName}</div>
              <div className="mt-1 font-medium">{r.productName}</div>
              <div className="mt-0.5 text-[13px] text-text-2">{joinDot(r.optionDisplayName, `수량 ${r.quantity}개`)}</div>
            </div>
            <div className="flex flex-col items-end gap-2 text-[13px] text-text-2">
              <StatusChip kind="return" status={r.status} />
              <span>
                주문번호{" "}
                <Link href={`/my/orders/${r.orderId}`} className="tabular-nums underline">
                  {r.orderNo}
                </Link>
              </span>
            </div>
          </section>
          {q.isError && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
          {needs && (
            <p role="status" className="rounded-lg border border-border bg-surface px-4 py-3 text-sm">
              {win.expired ? (
                <>
                  환불 확인이 아직 끝나지 않았습니다.{" "}
                  <button type="button" className="underline" onClick={() => void q.refetch()}>
                    새로고침
                  </button>
                </>
              ) : (
                "환불을 확인하고 있습니다. 이 화면은 3초마다 자동으로 새로 고쳐집니다."
              )}
            </p>
          )}
          <Section title="진행 단계">
            <ReturnSteps r={r} />
            {canWithdraw(r.status) && (
              <div className="mt-5 flex justify-end">
                <WithdrawReturnControl orderId={r.orderId} returnId={r.returnId} className="btn btn-ghost btn-sm" />
              </div>
            )}
          </Section>
          <Section title="반품 사유">
            <dl className="grid grid-cols-[96px_1fr] gap-y-2 text-sm">
              <dt className="text-text-2">사유</dt>
              <dd>{returnReasonLabel(r.reason)}</dd>
            </dl>
            {r.status === "REJECTED" && r.rejectReason && (
              <div role="alert" className="mt-4 rounded-lg border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm">
                <strong>거절 사유</strong>
                <br />
                {r.rejectReason}
              </div>
            )}
          </Section>
          <Section title="환불 내역">
            {r.status === "COMPLETED" || r.status === "REFUNDING" ? (
              <RefundBreakdown rows={refundRows(r, "환불 금액")} title="환불 금액" />
            ) : (
              <p className="text-sm text-text-2">환불 금액은 판매자가 상품을 받은 뒤 확정됩니다.</p>
            )}
          </Section>
        </div>
      )}
    </div>
  );
}
