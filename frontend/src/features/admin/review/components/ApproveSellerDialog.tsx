"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { commissionSchema } from "@/features/admin/review/reject-reason";
import { formatBp } from "@/lib/money";

export const DEFAULT_COMMISSION_BP = 1000;

/** 판매자 승인: 수수료율(bp) 정수 입력(기본 1000), 미리 보기는 formatBp. */
export function ApproveSellerDialog({
  open,
  companyName,
  submitting,
  onSubmit,
  onCancel,
}: {
  open: boolean;
  companyName: string;
  submitting: boolean;
  onSubmit: (commissionRateBp: number) => Promise<boolean>;
  onCancel: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && !submitting && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>판매자 입점 승인</DialogTitle>
          <DialogDescription>{companyName}의 입점을 승인합니다. 수수료율은 승인 뒤 정산에 적용됩니다.</DialogDescription>
        </DialogHeader>
        <ApproveForm submitting={submitting} onSubmit={onSubmit} onCancel={onCancel} />
      </DialogContent>
    </Dialog>
  );
}

type Input = { commissionRateBp: string };

function ApproveForm({ submitting, onSubmit, onCancel }: { submitting: boolean; onSubmit: (bp: number) => Promise<boolean>; onCancel: () => void }) {
  const form = useForm<Input, unknown, { commissionRateBp: number }>({
    resolver: zodResolver(commissionSchema),
    defaultValues: { commissionRateBp: String(DEFAULT_COMMISSION_BP) },
  });
  const { register, formState, watch } = form;
  const error = formState.errors.commissionRateBp?.message;
  const parsed = commissionSchema.safeParse({ commissionRateBp: watch("commissionRateBp") });
  const preview = parsed.success ? formatBp(parsed.data.commissionRateBp) : null;

  const submit = form.handleSubmit(async (v) => {
    if (submitting) return;
    await onSubmit(v.commissionRateBp);
  });

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <div className="field">
        <label htmlFor="commission-bp">수수료율 (bp)</label>
        <input
          id="commission-bp"
          inputMode="numeric"
          autoComplete="off"
          className="input"
          aria-invalid={error ? "true" : undefined}
          aria-describedby="commission-hint"
          {...register("commissionRateBp")}
        />
        {error ? (
          <p role="alert" className="field-error">{error}</p>
        ) : (
          <p id="commission-hint" className="hint">
            1bp = 0.01%. {preview ? <>수수료율 <strong>{preview}</strong></> : null}
          </p>
        )}
      </div>
      <DialogFooter>
        <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={submitting}>취소</button>
        <button type="submit" className="btn btn-primary" disabled={submitting}>{submitting ? "처리 중…" : "승인"}</button>
      </DialogFooter>
    </form>
  );
}
