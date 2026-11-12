"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { useSellerMe } from "@/features/seller/gate/useSellerMe";
import { canProcessReturns } from "@/features/seller/returns/actions";
import { useSellerReturnActions } from "@/features/seller/returns/api";
import { ReceiveDialog } from "@/features/seller/returns/components/ReceiveDialog";
import { RejectDialog } from "@/features/seller/returns/components/RejectDialog";
import { receiveOutcome } from "@/features/seller/returns/receive-outcome";
import { toast } from "@/hooks/use-toast";
import { joinDot } from "@/lib/join-dot";
import { formatKrw } from "@/lib/money";
import { queryKeys } from "@/lib/query-keys";

export type ReturnNotice = { tone: "info" | "warn"; text: string };

export const SUSPENDED_NOTICE = "정지 상태에서는 반품을 처리할 수 없습니다. 관리자가 대신 처리합니다";
export const RECEIVE_REJECTED = "환불이 거절되었습니다. 반품은 승인 상태로 돌아갔습니다. 잠시 후 다시 시도해 주세요";
export const RECEIVE_UNKNOWN = "결과를 확인하지 못했습니다. 반품 상태를 확인해 주세요";
const RECEIVE_UNKNOWN_NOTICE = "결과를 확인하지 못했습니다. 아래 상태를 확인한 뒤 판단해 주세요. 자동으로 다시 시도하지 않습니다.";

/**
 * 판매자 처리 영역: 승인·거절(REQUESTED), 수거 확인(APPROVED). 정지 판매자에게는 버튼 대신 안내만 보인다.
 * 수거 확인은 환불을 실행하므로 한 번만 보내고, 응답을 못 받아도 자동으로 다시 보내지 않는다(사용자가 상세를 보고 판단).
 * 모든 요청은 api 훅이 끝나면 상세·목록(배지 포함)을 다시 읽게 한다.
 */
export function SellerReturnActions({ data: r, onNotice }: { data: ReturnRequestResponse; onNotice: (n: ReturnNotice | null) => void }) {
  const { status: sellerStatus } = useSellerMe();
  const setBanner = useSellerBanner();
  const qc = useQueryClient();
  const { approve, reject, receive } = useSellerReturnActions(r.returnId);
  const [dialog, setDialog] = useState<"approve" | "reject" | "receive" | null>(null);
  const [busy, setBusy] = useState(false);
  // 환불이 접수(완료·확인 중)된 뒤에는 상세가 새 상태를 읽어 올 때까지 다시 보내지 않는다.
  const [receiveLocked, setReceiveLocked] = useState(false);
  const inFlight = useRef(false);

  const actionable = r.status === "REQUESTED" || r.status === "APPROVED";
  if (!actionable) return <p className="text-sm text-text-2">처리할 수 있는 작업이 없습니다.</p>;
  if (!canProcessReturns(sellerStatus)) {
    return (
      <div className="notice notice-warn" role="alert">
        <span>{SUSPENDED_NOTICE}</span>
      </div>
    );
  }

  // 한 번에 하나만 보낸다(ref 는 state 반영 전의 연타도 막는다).
  const guarded = async (fn: () => Promise<void>) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    try {
      await fn();
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  };

  const onFailure = (e: unknown) => {
    if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
      setBanner("판매자 상태 때문에 처리할 수 없습니다.");
      void qc.invalidateQueries({ queryKey: queryKeys.seller.me });
    } else {
      // 409 RETURN_INVALID_STATE_TRANSITION 포함: 서버 메시지를 보이고, 상세는 api 훅이 이미 다시 읽는다.
      toast({ title: resolveError(e).message, variant: "destructive" });
    }
  };

  const confirmApprove = () =>
    guarded(async () => {
      onNotice(null);
      try {
        await approve();
        toast({ title: "반품을 승인했습니다" });
      } catch (e) {
        onFailure(e);
      }
      setDialog(null);
    });

  const confirmReject = (reason: string) =>
    guarded(async () => {
      onNotice(null);
      try {
        await reject(reason);
        toast({ title: "반품을 거절했습니다" });
      } catch (e) {
        onFailure(e);
      }
      setDialog(null);
    });

  const confirmReceive = () =>
    guarded(async () => {
      onNotice(null);
      let outcome;
      try {
        const res = await receive();
        outcome = receiveOutcome({ ok: true, status: res.status, refundAmount: res.refundAmount });
      } catch (e) {
        outcome = receiveOutcome({ ok: false, error: e });
        if (outcome.kind === "error") onFailure(e);
      }
      switch (outcome.kind) {
        case "completed":
          setReceiveLocked(true);
          toast({ title: `환불을 완료했습니다. 환불 금액 ${formatKrw(outcome.refundAmount)}` });
          break;
        case "refunding":
          setReceiveLocked(true);
          toast({ title: "환불을 확인하고 있습니다" });
          break;
        case "rejected":
          toast({ title: RECEIVE_REJECTED, variant: "destructive" });
          onNotice({ tone: "warn", text: RECEIVE_REJECTED });
          break;
        case "unknown":
          toast({ title: RECEIVE_UNKNOWN, variant: "destructive" });
          onNotice({ tone: "warn", text: RECEIVE_UNKNOWN_NOTICE });
          break;
        case "error":
          break;
      }
      setDialog(null);
    });

  return (
    <div>
      {r.status === "REQUESTED" ? (
        <>
          <div className="flex gap-2">
            <button type="button" className="btn btn-primary" onClick={() => setDialog("approve")}>
              승인
            </button>
            <button type="button" className="btn btn-danger" onClick={() => setDialog("reject")}>
              거절
            </button>
          </div>
          <p className="hint mt-2">불량이 아니라고 판단되면 거절해 주세요. 관리자가 다시 확인합니다.</p>
        </>
      ) : (
        <>
          <button type="button" className="btn btn-primary" disabled={receiveLocked || busy} onClick={() => setDialog("receive")}>
            수거 확인
          </button>
          <p className="hint mt-2">상품을 받은 뒤 확인하면 구매자에게 환불이 실행됩니다.</p>
        </>
      )}

      <ConfirmDialog
        open={dialog === "approve"}
        title="반품 승인"
        description="이 반품을 승인할까요? 승인하면 구매자가 상품을 보내고, 받은 뒤 수거 확인을 하면 환불이 진행됩니다. 반품 사유는 바꿀 수 없습니다. 불량이 아니라고 판단되면 거절해 주세요. 관리자가 다시 확인합니다."
        confirmLabel={busy ? "처리 중…" : "승인"}
        onCancel={() => !busy && setDialog(null)}
        onConfirm={() => void confirmApprove()}
      />
      {dialog === "reject" && <RejectDialog busy={busy} onSubmit={confirmReject} onClose={() => setDialog(null)} />}
      {dialog === "receive" && (
        <ReceiveDialog
          reason={r.reason}
          busy={busy}
          onConfirm={confirmReceive}
          onClose={() => setDialog(null)}
          summary={
            <div className="flex items-center gap-3">
              <OrderThumb url={r.thumbnailUrl} size={56} />
              <div className="min-w-0">
                <div className="truncate font-medium">{r.productName}</div>
                <div className="text-[13px] text-text-2">{joinDot(r.optionDisplayName, `${r.quantity}개`)}</div>
                {r.sellerBorneFee > 0 && <div className="mt-1 text-[13px] font-medium">판매자 부담 반품비 {formatKrw(r.sellerBorneFee)} — 정산에서 차감됩니다</div>}
              </div>
            </div>
          }
        />
      )}
    </div>
  );
}
