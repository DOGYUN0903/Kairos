"use client";

import { useState } from "react";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useAdminReturnActions } from "@/features/admin/returns/api";
import { adminReturnActions } from "@/features/admin/returns/params";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { useReceiveReturn, type ReturnNotice } from "@/features/returns/useReceiveReturn";
import { ReceiveDialog } from "@/features/seller/returns/components/ReceiveDialog";
import { toast } from "@/hooks/use-toast";
import { joinDot } from "@/lib/join-dot";

export const NOT_ELIGIBLE = "이미 구매 확정된 주문이라 승인할 수 없습니다. 정산 조정으로 처리해 주세요";
export const CANCEL_CONFIRM = "구매자 요청으로 반품을 중단합니다. 상품은 주문 상태로 돌아가며, 구매자는 이 상품을 다시 반품 요청할 수 없습니다";

/**
 * 관리자 처리 영역: 승인(REQUESTED·REJECTED), 대신 수거 확인·중단(APPROVED).
 * 대신 수거 확인은 판매자 화면(SellerReturnActions)과 같은 안전 규칙으로 환불을 실행한다:
 * 한 번만 보내고(ref 가드), 응답을 못 받아도 자동으로 다시 보내지 않으며, 접수된 뒤에는 서버 상태를 따라 버튼을 잠근다.
 */
export function AdminReturnActions({ data: r, onNotice }: { data: ReturnRequestResponse; onNotice: (n: ReturnNotice | null) => void }) {
  const { approve, receive, cancel } = useAdminReturnActions(r.returnId);
  const [dialog, setDialog] = useState<"approve" | "receive" | "cancel" | null>(null);
  // 서버 메시지를 그대로 보인다. 상세는 api 훅이 이미 다시 읽는다.
  const onFailure = (e: unknown) => toast({ title: resolveError(e).message, variant: "destructive" });
  const { busy, locked, guarded, confirmReceive: runReceive } = useReceiveReturn({ status: r.status, receive, onNotice, onError: onFailure });

  const actions = adminReturnActions(r.status);
  if (actions.length === 0) return r.status === "REFUNDING" ? null : <p className="text-sm text-text-2">처리할 수 있는 작업이 없습니다.</p>;

  const confirmApprove = () =>
    guarded(async () => {
      onNotice(null);
      try {
        await approve();
        toast({ title: "반품을 승인했습니다" });
      } catch (e) {
        // 구매 확정된 주문의 승인 거부는 승인에만 의미가 있는 안내다. 수거 확인·중단은 서버 메시지를 그대로 보인다.
        if (e instanceof ApiError && e.code === "RETURN_NOT_ELIGIBLE") toast({ title: NOT_ELIGIBLE, variant: "destructive" });
        else onFailure(e);
      }
      setDialog(null);
    });

  const confirmCancel = () =>
    guarded(async () => {
      onNotice(null);
      try {
        await cancel();
        toast({ title: "반품을 중단했습니다" });
      } catch (e) {
        onFailure(e);
      }
      setDialog(null);
    });

  const confirmReceive = () => runReceive(() => setDialog(null));

  return (
    <div>
      {actions.includes("approve") && (
        <>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => setDialog("approve")}>
            승인
          </button>
          <p className="hint mt-2">{r.status === "REJECTED" ? "판매자가 거절한 반품을 다시 승인합니다." : "판매자를 대신해 반품을 승인합니다."}</p>
        </>
      )}
      {actions.includes("receive") && (
        <>
          <div className="flex gap-2">
            <button type="button" className="btn btn-primary" disabled={locked || busy} onClick={() => setDialog("receive")}>
              대신 수거 확인
            </button>
            <button type="button" className="btn btn-danger" disabled={locked || busy} onClick={() => setDialog("cancel")}>
              중단
            </button>
          </div>
          <p className="hint mt-2">상품을 받은 것을 확인한 뒤 수거 확인을 하면 구매자에게 환불이 실행됩니다.</p>
        </>
      )}

      <ConfirmDialog
        open={dialog === "approve"}
        title="반품 승인"
        description="이 반품을 승인할까요? 승인하면 구매자가 상품을 보내고, 받은 뒤 수거 확인을 하면 환불이 진행됩니다."
        confirmLabel={busy ? "처리 중…" : "승인"}
        onCancel={() => !busy && setDialog(null)}
        onConfirm={() => void confirmApprove()}
      />
      <ConfirmDialog
        open={dialog === "cancel"}
        title="반품 중단"
        description={CANCEL_CONFIRM}
        confirmLabel={busy ? "처리 중…" : "중단"}
        destructive
        onCancel={() => !busy && setDialog(null)}
        onConfirm={() => void confirmCancel()}
      />
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
                {r.feeBearer === "SELLER" && <div className="mt-1 text-[13px] font-medium">판매자 부담 반품입니다. 반품 배송비는 정산에서 차감됩니다.</div>}
              </div>
            </div>
          }
        />
      )}
    </div>
  );
}
