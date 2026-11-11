"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { REASON_MAX, rejectReasonSchema, type RejectReasonValues } from "@/features/admin/review/reject-reason";

/** 정지 사유 입력(1..500자 필수, ReasonRequest). 정지하면 판매자 콘솔에 사유가 배너로 보이고 상품이 공개 목록에서 내려간다. */
export function SuspendDialog({
  open,
  companyName,
  submitting,
  onSubmit,
  onCancel,
}: {
  open: boolean;
  companyName: string;
  submitting: boolean;
  onSubmit: (reason: string) => Promise<boolean>;
  onCancel: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && !submitting && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{companyName} 정지</DialogTitle>
          <DialogDescription>정지하면 이 판매자의 상품이 공개 목록에서 내려가고 새 상품·브랜드를 등록할 수 없습니다. 사유는 판매자에게 그대로 전달됩니다.</DialogDescription>
        </DialogHeader>
        <SuspendForm submitting={submitting} onSubmit={onSubmit} onCancel={onCancel} />
      </DialogContent>
    </Dialog>
  );
}

function SuspendForm({ submitting, onSubmit, onCancel }: { submitting: boolean; onSubmit: (reason: string) => Promise<boolean>; onCancel: () => void }) {
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
        <label htmlFor="suspend-reason">
          정지 사유 <span className="text-xs font-normal text-text-2">(필수)</span>
        </label>
        <textarea
          id="suspend-reason"
          rows={4}
          className="input min-h-[96px] py-2"
          placeholder="예: 허위 상품 정보 신고가 접수되어 확인 중입니다."
          aria-invalid={error ? "true" : undefined}
          aria-describedby={error ? "suspend-reason-error" : "suspend-reason-count"}
          {...register("reason")}
        />
        {error ? <p id="suspend-reason-error" role="alert" className="field-error">{error}</p> : <p id="suspend-reason-count" className="hint">{length}/{REASON_MAX}</p>}
      </div>
      <DialogFooter>
        <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={submitting}>취소</button>
        <button type="submit" className="btn btn-destructive" disabled={submitting}>{submitting ? "처리 중…" : "정지"}</button>
      </DialogFooter>
    </form>
  );
}
