"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { applyFormError } from "@/features/auth/apply-form-error";
import { FormNotice } from "@/features/auth/components/FormNotice";
import { useApply, useUpdateApplication, type SellerDetail } from "@/features/seller/apply/api";
import { applySchema, BANKS, type ApplyValues } from "@/features/seller/apply/schemas";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

const EMPTY: ApplyValues = {
  companyName: "",
  businessNumber: "",
  representativeName: "",
  phone: "",
  bank: BANKS[0],
  accountNumber: "",
  holder: "",
};

function defaultsOf(seller?: SellerDetail): ApplyValues {
  if (!seller) return EMPTY;
  return {
    companyName: seller.companyName,
    businessNumber: seller.businessNumber,
    representativeName: seller.representativeName,
    phone: seller.phone,
    bank: seller.bank,
    accountNumber: seller.accountNumber,
    holder: seller.holder,
  };
}

/** create = 신규 신청(POST), update = 심사 중·반려 상태에서 고쳐 다시 신청(PUT). */
export function ApplyForm({ mode, seller }: { mode: "create" | "update"; seller?: SellerDetail }) {
  const router = useRouter();
  const qc = useQueryClient();
  const apply = useApply();
  const update = useUpdateApplication();
  const form = useForm<ApplyValues>({ resolver: zodResolver(applySchema), defaultValues: defaultsOf(seller) });
  const { register, formState } = form;
  const { errors } = formState;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      if (mode === "create") await apply.mutateAsync({ values, key });
      else await update.mutateAsync({ values });
      // 신청이 끝난 입력을 남기지 않게 성공 뒤 폼을 비운다. Idempotency-Key 와는 무관하다(키는 성공하면 훅이 버린다).
      if (mode === "create") form.reset();
      toast({ title: mode === "create" ? "입점을 신청했습니다." : "신청 내용을 수정해 다시 접수했습니다." });
      router.replace("/seller");
    },
    {
      onError: async (e) => {
        if (e instanceof ApiError && e.code === "SELLER_ALREADY_EXISTS") {
          // 이미 신청한 계정: 상태를 다시 읽고 게이트가 맞는 화면을 고르게 한다.
          await qc.invalidateQueries({ queryKey: queryKeys.seller.me });
          router.replace("/seller");
          return;
        }
        applyFormError(form, e);
      },
    },
  );

  const field = (name: keyof ApplyValues, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => {
    const err = errors[name];
    return (
      <div className="field">
        <label htmlFor={`a-${name}`}>{label}</label>
        <input
          id={`a-${name}`}
          className="input"
          aria-invalid={err ? true : undefined}
          aria-describedby={err ? `a-${name}-err` : undefined}
          {...props}
          {...register(name)}
        />
        {err && (
          <span id={`a-${name}-err`} className="field-error">
            {err.message}
          </span>
        )}
      </div>
    );
  };

  const banks: readonly string[] = seller && !BANKS.some((b) => b === seller.bank) ? [seller.bank, ...BANKS] : BANKS;

  return (
    <form onSubmit={onSubmit} noValidate className="card">
      <div className="border-b border-border px-5 py-4">
        <h2 className="text-[15px] font-bold">{mode === "create" ? "입점 신청" : "입점 신청 수정"}</h2>
      </div>
      <div className="p-5">
        <fieldset disabled={submitting} className="flex flex-col gap-4">
          {errors.root?.server && <FormNotice message={errors.root.server.message} />}
          <div className="grid grid-cols-2 gap-4">
            {field("companyName", "회사명", { autoComplete: "organization" })}
            {field("businessNumber", "사업자등록번호", { inputMode: "numeric", placeholder: "123-45-67890" })}
            {field("representativeName", "대표자")}
            {field("phone", "연락처", { type: "tel", autoComplete: "tel" })}
            <div className="field">
              <label htmlFor="a-bank">정산 은행</label>
              <select id="a-bank" className="input" aria-invalid={errors.bank ? true : undefined} {...register("bank")}>
                {banks.map((b) => (
                  <option key={b} value={b}>
                    {b}
                  </option>
                ))}
              </select>
              {errors.bank && <span className="field-error">{errors.bank.message}</span>}
            </div>
            {field("accountNumber", "계좌번호", { inputMode: "numeric" })}
            {field("holder", "예금주")}
          </div>
          <div className="mt-2 flex justify-end">
            <button type="submit" className="btn btn-primary">
              {submitting ? "접수 중…" : mode === "create" ? "신청하기" : "다시 신청"}
            </button>
          </div>
        </fieldset>
      </div>
    </form>
  );
}
