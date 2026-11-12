"use client";

import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { formatKrw, formatKrwMinus } from "@/lib/money";

export const PAY_MEMO_MAX = 200;

/** 지급 완료 창. 메모는 선택(200자). onSubmit 은 호출하는 쪽이 처리 중 재호출을 막는다. */
export function PayDialog({
  summary,
  totalAmount,
  payableAmount,
  busy,
  onSubmit,
  onCancel,
}: {
  summary: string;
  totalAmount: number;
  payableAmount: number;
  busy: boolean;
  onSubmit: (memo: string) => void;
  onCancel: () => void;
}) {
  const [memo, setMemo] = useState("");
  const nothingToPay = payableAmount === 0;
  return (
    <Dialog open onOpenChange={(open) => !open && !busy && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>지급 완료 처리</DialogTitle>
          <DialogDescription>
            {nothingToPay ? "지급할 금액이 없는 정산입니다. 지급 완료로 닫습니다" : `지급액 ${formatKrw(payableAmount)}을 지급 완료로 표시합니다(실제 이체는 따로 진행합니다)`}
          </DialogDescription>
        </DialogHeader>
        <div className="rounded border border-border bg-surface px-4 py-3 text-sm">
          <div className="text-xs text-text-2">정산</div>
          <div>{summary}</div>
          {nothingToPay && <div className="mt-1 tabular-nums">합계 {formatKrwMinus(totalAmount)} · 지급액 0원</div>}
        </div>
        <div className="field">
          <label htmlFor="pay-memo">
            메모 <span className="font-normal text-text-2">(선택)</span>
          </label>
          <input id="pay-memo" className="input" maxLength={PAY_MEMO_MAX} value={memo} disabled={busy} onChange={(e) => setMemo(e.target.value)} />
          <span className="counter ml-auto text-xs text-text-3">
            {memo.length} / {PAY_MEMO_MAX}
          </span>
        </div>
        <DialogFooter>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={onCancel}>
            취소
          </button>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => onSubmit(memo.trim())}>
            지급 완료
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
