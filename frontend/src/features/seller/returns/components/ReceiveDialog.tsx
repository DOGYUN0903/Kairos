"use client";

import { useRef, type ReactNode } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { returnReasonLabel } from "@/features/returns/return-text";

export const RECEIVE_WARNING = "상품을 받으셨나요? 확인하면 구매자에게 환불이 실행되며 되돌릴 수 없습니다.";

/** 사유별 재고 안내. 변심만 서버가 재고를 복구한다. */
export function stockNotice(reason: ReturnRequestResponse["reason"]): string {
  return reason === "CHANGE_OF_MIND"
    ? "단순 변심 반품이라 환불이 완료되면 재고가 자동으로 복구됩니다."
    : "재고는 자동으로 복구되지 않습니다. 재판매할 수 있으면 상품 관리에서 재고를 수정해 주세요.";
}

/**
 * 수거 확인 창(판매자·관리자 공용). 확인하면 환불이 실행되므로 요청은 한 번만 나간다:
 * 처리 중(busy)에는 버튼을 막고, 같은 틱의 연타는 ref 로 막는다. 마운트되어 있는 동안 열려 있다(호출 쪽이 닫는다).
 * onConfirm 은 결과 처리와 창 닫기까지 호출 쪽이 한다. 실패해도 이 창은 다시 보내지 않는다.
 */
export function ReceiveDialog({ reason, onConfirm, busy, onClose, summary }: { reason: ReturnRequestResponse["reason"]; onConfirm: () => Promise<void>; busy: boolean; onClose: () => void; summary?: ReactNode }) {
  const sending = useRef(false);
  const confirm = async () => {
    if (sending.current || busy) return;
    sending.current = true;
    try {
      await onConfirm();
    } finally {
      sending.current = false;
    }
  };
  return (
    <Dialog open onOpenChange={(open) => !open && !busy && onClose()}>
      <DialogContent className="max-w-[460px]">
        <DialogHeader>
          <DialogTitle>수거 확인 · {returnReasonLabel(reason)}</DialogTitle>
          <DialogDescription>{RECEIVE_WARNING}</DialogDescription>
        </DialogHeader>
        {summary}
        <p className="text-sm text-text-2">{stockNotice(reason)}</p>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={onClose}>
            취소
          </button>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void confirm()}>
            {busy ? "처리 중…" : "수거 확인 · 환불 실행"}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
