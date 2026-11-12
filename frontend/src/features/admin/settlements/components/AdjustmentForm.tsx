"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { resolveError } from "@/api/errors";
import { useAdminAdjustmentActions } from "@/features/admin/settlements/api";
import {
  adjustmentFormSchema,
  REASON_MAX,
  toAdjustmentRequest,
  type AdjustmentFormInput,
  type AdjustmentFormValues,
} from "@/features/admin/settlements/adjustment-schema";
import { toast } from "@/hooks/use-toast";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { formatSignedKrw, parseWon, signedAmount } from "@/lib/money";

export const ADJUSTMENT_NOTICE = "조정은 다음에 만들어지는 정산에 반영됩니다. 이미 끝난 기간의 정산을 아직 생성하지 않았다면, 그 기간이 끝난 뒤 등록한 조정은 그다음 정산에 붙습니다";

/** 정산 조정 등록 양식. 같은 값 재제출·재시도는 useIdempotentSubmit 이 같은 키로 보낸다. */
export function AdjustmentForm({ sellerId, onDone, onCancel }: { sellerId: number; onDone: () => void; onCancel: () => void }) {
  const form = useForm<AdjustmentFormInput, unknown, AdjustmentFormValues>({
    resolver: zodResolver(adjustmentFormSchema),
    defaultValues: { kind: "ADD", amount: "", reason: "", refSellerOrderId: "" },
  });
  const { create } = useAdminAdjustmentActions(sellerId);
  const [formError, setFormError] = useState<string | null>(null);
  const { register, watch, formState } = form;
  const { errors } = formState;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      setFormError(null);
      await create(toAdjustmentRequest(values), key);
      toast({ title: "조정을 등록했습니다" });
      onDone();
    },
    {
      onError: (e) => {
        const r = resolveError(e);
        if (r.kind === "form") setFormError(r.message);
        else toast({ title: r.message, variant: "destructive" });
      },
    },
  );

  const kind = watch("kind");
  const amount = parseWon(watch("amount"));
  const reason = watch("reason");
  const err = (k: keyof AdjustmentFormInput) => errors[k]?.message as string | undefined;

  return (
    <section className="card" aria-label="조정 등록">
      <div className="border-b border-border px-5 py-4 text-[15px] font-bold">조정 등록 · 판매자 {sellerId}</div>
      <form
        noValidate
        className="flex flex-col gap-4 p-5"
        onSubmit={(e) => {
          if (submitting) {
            e.preventDefault();
            return;
          }
          void onSubmit(e);
        }}
      >
        {formError && (
          <div role="alert" className="notice notice-warn">
            <span>{formError}</span>
          </div>
        )}
        <fieldset className="flex flex-col gap-2" disabled={submitting}>
          <legend className="mb-1 text-[13px] font-bold">
            구분<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
          </legend>
          <div role="radiogroup" aria-label="구분" className="flex gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" value="ADD" className="accent-[#1b2a4a]" {...register("kind")} />
              가산
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" value="SUBTRACT" className="accent-[#1b2a4a]" {...register("kind")} />
              차감
            </label>
          </div>
        </fieldset>
        <div className="field max-w-[320px]">
          <label htmlFor="adj-amount">
            금액<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
          </label>
          <div className="flex items-center gap-2">
            <input id="adj-amount" className="input tabular-nums" inputMode="numeric" disabled={submitting} aria-invalid={errors.amount ? true : undefined} {...register("amount")} />
            <span className="text-sm text-text-2">원</span>
          </div>
          {err("amount") ? (
            <span role="alert" className="field-error">
              {err("amount")}
            </span>
          ) : (
            kind === "SUBTRACT" &&
            amount !== null &&
            amount > 0 && <span className="hint">차감은 정산에서 {formatSignedKrw(signedAmount("SUBTRACT", amount))}으로 반영됩니다.</span>
          )}
        </div>
        <div className="field">
          <label htmlFor="adj-reason">
            사유<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
          </label>
          <textarea id="adj-reason" className="input" rows={3} disabled={submitting} aria-invalid={errors.reason ? true : undefined} {...register("reason")} />
          <div className="flex justify-between">
            {err("reason") ? (
              <span role="alert" className="field-error">
                {err("reason")}
              </span>
            ) : (
              <span />
            )}
            <span className="counter ml-auto text-xs text-text-3">
              {reason.length} / {REASON_MAX}
            </span>
          </div>
        </div>
        <div className="field max-w-[320px]">
          <label htmlFor="adj-ref">
            근거 판매자 주문 ID <span className="font-normal text-text-2">(선택)</span>
          </label>
          <input id="adj-ref" className="input tabular-nums" inputMode="numeric" disabled={submitting} aria-invalid={errors.refSellerOrderId ? true : undefined} {...register("refSellerOrderId")} />
          {err("refSellerOrderId") && (
            <span role="alert" className="field-error">
              {err("refSellerOrderId")}
            </span>
          )}
        </div>
        <div className="notice">
          <span>{ADJUSTMENT_NOTICE}</span>
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" className="btn btn-ghost" disabled={submitting} onClick={onCancel}>
            취소
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            등록
          </button>
        </div>
      </form>
    </section>
  );
}
