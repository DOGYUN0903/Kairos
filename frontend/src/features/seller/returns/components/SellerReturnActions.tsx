"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { useReceiveReturn, type ReturnNotice } from "@/features/returns/useReceiveReturn";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { useSellerMe } from "@/features/seller/gate/useSellerMe";
import { canProcessReturns } from "@/features/seller/returns/actions";
import { useSellerReturnActions } from "@/features/seller/returns/api";
import { ReceiveDialog } from "@/features/seller/returns/components/ReceiveDialog";
import { RejectDialog } from "@/features/seller/returns/components/RejectDialog";
import { toast } from "@/hooks/use-toast";
import { joinDot } from "@/lib/join-dot";
import { queryKeys } from "@/lib/query-keys";

export const SUSPENDED_NOTICE = "정지 상태에서는 반품을 처리할 수 없습니다. 관리자가 대신 처리합니다";

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
  const onFailure = (e: unknown) => {
    if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
      setBanner("판매자 상태 때문에 처리할 수 없습니다.");
      void qc.invalidateQueries({ queryKey: queryKeys.seller.me });
    } else {
      // 409 RETURN_INVALID_STATE_TRANSITION 포함: 서버 메시지를 보이고, 상세는 api 훅이 이미 다시 읽는다.
      toast({ title: resolveError(e).message, variant: "destructive" });
    }
  };
  const { busy, locked, guarded, confirmReceive: runReceive } = useReceiveReturn({ status: r.status, receive, onNotice, onError: onFailure });

  const actionable = r.status === "REQUESTED" || r.status === "APPROVED";
  // 환불 확인 중에는 상세 쪽 안내가 있으므로 빈 안내를 따로 보이지 않는다.
  if (!actionable) return r.status === "REFUNDING" ? null : <p className="text-sm text-text-2">처리할 수 있는 작업이 없습니다.</p>;
  if (!canProcessReturns(sellerStatus)) {
    return (
      <div className="notice notice-warn" role="alert">
        <span>{SUSPENDED_NOTICE}</span>
      </div>
    );
  }

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

  // 실패하면 창을 열어 둔다(입력한 사유를 잃지 않는다). 닫는 것은 성공했을 때뿐.
  const confirmReject = (reason: string) =>
    guarded(async () => {
      onNotice(null);
      try {
        await reject(reason);
        toast({ title: "반품을 거절했습니다" });
        setDialog(null);
      } catch (e) {
        onFailure(e);
      }
    });

  const confirmReceive = () => runReceive(() => setDialog(null));

  return (
    <div>
      {r.status === "REQUESTED" ? (
        <>
          <div className="flex gap-2">
            <button type="button" className="btn btn-primary" disabled={busy} onClick={() => setDialog("approve")}>
              승인
            </button>
            <button type="button" className="btn btn-danger" disabled={busy} onClick={() => setDialog("reject")}>
              거절
            </button>
          </div>
          <p className="hint mt-2">불량이 아니라고 판단되면 거절해 주세요. 관리자가 다시 확인합니다.</p>
        </>
      ) : (
        <>
          <button type="button" className="btn btn-primary" disabled={locked || busy} onClick={() => setDialog("receive")}>
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
                {/* 반품비 금액은 수거 확인 때 서버가 정하므로(승인 상태에서는 0) 금액 없이 안내한다. */}
                {r.feeBearer === "SELLER" && <div className="mt-1 text-[13px] font-medium">판매자 부담 반품입니다. 반품 배송비는 정산에서 차감됩니다.</div>}
              </div>
            </div>
          }
        />
      )}
    </div>
  );
}
