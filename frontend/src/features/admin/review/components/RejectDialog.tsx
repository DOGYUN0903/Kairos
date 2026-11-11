"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { REASON_MAX, rejectReasonSchema, type RejectReasonValues } from "@/features/admin/review/reject-reason";

/**
 * 반려 사유 입력(1..500자 필수). 사유는 판매자에게 그대로 전달된다.
 * onSubmit 이 true 를 돌려주면(성공) 닫는다. 처리 중에는 버튼을 막는다.
 */
export function RejectDialog({
  open,
  title,
  submitting,
  onSubmit,
  onCancel,
}: {
  open: boolean;
  title: string;
  submitting: boolean;
  onSubmit: (reason: string) => Promise<boolean>;
  onCancel: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && !submitting && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>반려 사유는 판매자에게 그대로 전달됩니다.</DialogDescription>
        </DialogHeader>
        <RejectForm submitting={submitting} onSubmit={onSubmit} onCancel={onCancel} />
      </DialogContent>
    </Dialog>
  );
}

// 다이얼로그가 닫히면 내용이 언마운트되어 폼이 열릴 때마다 새로 시작한다.
function RejectForm({ submitting, onSubmit, onCancel }: { submitting: boolean; onSubmit: (reason: string) => Promise<boolean>; onCancel: () => void }) {
  const form = useForm<RejectReasonValues>({ resolver: zodResolver(rejectReasonSchema), defaultValues: { reason: "" } });
  const { register, formState, watch } = form;
  const error = formState.errors.reason?.message;
  const length = watch("reason").length;

  const submit = form.handleSubmit(async ({ reason }) => {
    if (submitting) return;
    if (await onSubmit(reason)) form.reset();
  });

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <div className="field">
        <label htmlFor="reject-reason">
          반려 사유 <span className="text-xs font-normal text-text-2">(필수)</span>
        </label>
        <textarea
          id="reject-reason"
          rows={4}
          className="input min-h-[96px] py-2"
          placeholder="예: 추가된 이미지에 타 브랜드 로고가 포함되어 있습니다."
          aria-invalid={error ? "true" : undefined}
          aria-describedby={error ? "reject-reason-error" : "reject-reason-count"}
          {...register("reason")}
        />
        {error ? (
          <p id="reject-reason-error" role="alert" className="field-error">{error}</p>
        ) : (
          <p id="reject-reason-count" className="hint">{length}/{REASON_MAX}</p>
        )}
      </div>
      <DialogFooter>
        <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={submitting}>취소</button>
        <button type="submit" className="btn btn-destructive" disabled={submitting}>{submitting ? "처리 중…" : "반려"}</button>
      </DialogFooter>
    </form>
  );
}
