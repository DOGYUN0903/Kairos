"use client";

import { useRef, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";

export const REJECT_REASON_MAX = 500;
export const REJECT_NOTICE = "거절하면 구매자는 같은 상품을 다시 반품 요청할 수 없습니다.";

/** 반품 거절 창. 사유 필수(공백만은 비어 있는 것), 1~500자. onSubmit 은 결과 처리와 창 닫기까지 호출 쪽이 한다. */
export function RejectDialog({ onSubmit, busy, onClose }: { onSubmit: (reason: string) => Promise<void>; busy: boolean; onClose: () => void }) {
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const sending = useRef(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (sending.current || busy) return;
    const reason = value.trim();
    if (reason.length === 0) {
      setError("거절 사유를 입력해 주세요.");
      return;
    }
    if (reason.length > REJECT_REASON_MAX) {
      setError(`거절 사유는 ${REJECT_REASON_MAX}자 이하로 입력해 주세요.`);
      return;
    }
    setError(null);
    sending.current = true;
    try {
      await onSubmit(reason);
    } finally {
      sending.current = false;
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && !busy && onClose()}>
      <DialogContent className="max-w-[460px]">
        <DialogHeader>
          <DialogTitle>반품 거절</DialogTitle>
          <DialogDescription className="sr-only">거절 사유를 입력합니다.</DialogDescription>
        </DialogHeader>
        <form onSubmit={(e) => void submit(e)} noValidate className="flex flex-col gap-4">
          <div className="field">
            <label htmlFor="reject-reason">
              거절 사유<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
              <span className="sr-only"> (필수)</span>
            </label>
            <textarea
              id="reject-reason"
              rows={4}
              maxLength={REJECT_REASON_MAX}
              required
              disabled={busy}
              className="input min-h-[100px] py-2"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? "reject-reason-err reject-reason-count" : "reject-reason-count"}
              value={value}
              onChange={(e) => setValue(e.target.value)}
            />
            <div className="flex justify-between">
              <span id="reject-reason-err" role={error ? "alert" : undefined} className="field-error">
                {error}
              </span>
              <span id="reject-reason-count" className="text-xs tabular-nums text-text-3">
                {value.length} / {REJECT_REASON_MAX}
              </span>
            </div>
          </div>
          <p className="text-sm text-text-2">{REJECT_NOTICE}</p>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-ghost" disabled={busy} onClick={onClose}>
              취소
            </button>
            <button type="submit" className="btn btn-danger" disabled={busy}>
              {busy ? "처리 중…" : "거절"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
