"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMemo, useState, type ReactNode } from "react";
import { FormProvider, useForm, type UseFormRegisterReturn } from "react-hook-form";
import { resolveError } from "@/api/errors";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  defaultPolicyForm,
  NAME_MAX,
  policyFormSchema,
  toPolicyRequest,
  type PolicyFormInput,
  type PolicyFormValues,
  type PolicyScope,
} from "@/features/coupon-policies/schema";
import type { CreateCouponPolicyRequest } from "@/features/coupon-policies/policy-status";
import { toast } from "@/hooks/use-toast";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";

type Props = {
  scope: PolicyScope;
  /** 판매자만: "상품 하나"를 고르는 선택 상자. 폼 컨텍스트(useFormContext<PolicyFormInput>)로 targetProductId 를 등록한다. */
  productPicker?: ReactNode;
  /** 실패하면 던진다(useIdempotentSubmit 이 키를 정한다). 성공하면 창이 닫힌다. */
  onSubmit: (body: CreateCouponPolicyRequest, key: string) => Promise<void>;
  notice: string[];
  onClose: () => void;
};

function Choice({ value, label, description, disabled, register }: { value: string; label: string; description: string; disabled: boolean; register: UseFormRegisterReturn }) {
  return (
    <label className="flex flex-1 cursor-pointer items-start gap-2.5 rounded border border-border p-3 has-[:checked]:border-navy has-[:checked]:bg-surface">
      <input type="radio" value={value} disabled={disabled} className="mt-0.5 h-[18px] w-[18px] accent-[#1b2a4a]" aria-label={label} {...register} />
      <span className="flex flex-col text-[13px]">
        <b className="font-bold text-foreground">{label}</b>
        <span className="text-text-2">{description}</span>
      </span>
    </label>
  );
}

function Suffix({ unit, children }: { unit: string; children: ReactNode }) {
  return (
    <div className="flex items-center gap-2">
      {children}
      <span className="text-sm text-text-2">{unit}</span>
    </div>
  );
}

/** 쿠폰 정책 등록 창. 판매자·관리자가 함께 쓴다(scope 로 종류·대상 규칙이 다르다). 마운트되어 있는 동안 열려 있다. */
export function PolicyFormDialog({ scope, productPicker, onSubmit: submitPolicy, notice, onClose }: Props) {
  const schema = useMemo(() => policyFormSchema(scope), [scope]);
  const form = useForm<PolicyFormInput, unknown, PolicyFormValues>({ resolver: zodResolver(schema), defaultValues: defaultPolicyForm(scope) });
  const { register, watch, formState } = form;
  const { errors } = formState;
  const [formError, setFormError] = useState<string | null>(null);

  const couponType = scope === "seller" ? "PRODUCT" : watch("couponType");
  const targetType = watch("targetType");
  const discountType = watch("discountType");
  const name = watch("name");

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      setFormError(null);
      await submitPolicy(toPolicyRequest(values, scope), key);
      toast({ title: "쿠폰 정책을 등록했습니다" });
      onClose();
    },
    {
      onError: (e) => {
        const r = resolveError(e);
        if (r.kind === "form") setFormError(r.message);
        else toast({ title: r.message, variant: "destructive" });
      },
    },
  );

  const err = (k: keyof PolicyFormInput) => errors[k]?.message as string | undefined;
  const renderErr = (k: keyof PolicyFormInput, id: string) =>
    err(k) ? (
      <span id={id} role="alert" className="field-error">
        {err(k)}
      </span>
    ) : null;

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-h-[92vh] max-w-[680px] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>쿠폰 등록</DialogTitle>
          <DialogDescription className="sr-only">쿠폰 이름, 적용 대상, 할인, 사용 조건, 발급 기간을 입력합니다.</DialogDescription>
        </DialogHeader>
        <FormProvider {...form}>
          <form
            noValidate
            className="flex flex-col gap-5"
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

            <div className="field">
              <label htmlFor="pf-name">
                쿠폰 이름<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
              </label>
              <input id="pf-name" className="input" maxLength={NAME_MAX} placeholder="예: 첫 구매 10% 쿠폰" disabled={submitting} aria-invalid={errors.name ? true : undefined} {...register("name")} />
              <div className="flex justify-between">
                {renderErr("name", "pf-name-err")}
                <span className="counter ml-auto text-xs text-text-3">
                  {name.length} / {NAME_MAX}
                </span>
              </div>
            </div>

            {scope === "admin" && (
              <fieldset className="flex flex-col gap-2" disabled={submitting}>
                <legend className="mb-1 text-[13px] font-bold">쿠폰 종류</legend>
                <div role="radiogroup" aria-label="쿠폰 종류" className="flex gap-2">
                  <Choice value="PRODUCT" label="상품 쿠폰" description="상품에 적용합니다" disabled={submitting} register={register("couponType", { onChange: () => form.setValue("targetType", "ALL_PRODUCTS") })} />
                  <Choice value="CART" label="장바구니 쿠폰" description="주문 전체에 적용합니다" disabled={submitting} register={register("couponType", { onChange: () => form.setValue("targetType", "NONE") })} />
                </div>
              </fieldset>
            )}

            {couponType === "PRODUCT" && (
              <fieldset className="flex flex-col gap-2" disabled={submitting}>
                <legend className="mb-1 text-[13px] font-bold">적용 대상</legend>
                <div role="radiogroup" aria-label="적용 대상" className="flex gap-2">
                  {scope === "seller" ? (
                    <Choice value="SELLER" label="내 상품 전체" description="내가 판매하는 모든 상품에 사용할 수 있습니다" disabled={submitting} register={register("targetType")} />
                  ) : (
                    <Choice value="ALL_PRODUCTS" label="전 상품" description="모든 상품에 사용할 수 있습니다" disabled={submitting} register={register("targetType")} />
                  )}
                  <Choice value="PRODUCT" label="상품 하나" description={scope === "seller" ? "내 상품 중 고른 상품에만 사용할 수 있습니다" : "상품 ID로 지정한 상품에만 사용할 수 있습니다"} disabled={submitting} register={register("targetType")} />
                </div>
                {renderErr("targetType", "pf-target-err")}
                {targetType === "PRODUCT" &&
                  (scope === "seller" ? (
                    productPicker
                  ) : (
                    <div className="field mt-1 max-w-[320px]">
                      <label htmlFor="pf-product-id">
                        상품 ID<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
                      </label>
                      <input id="pf-product-id" className="input" inputMode="numeric" placeholder="예: 1024" disabled={submitting} aria-invalid={errors.targetProductId ? true : undefined} {...register("targetProductId")} />
                      {renderErr("targetProductId", "pf-product-id-err")}
                    </div>
                  ))}
              </fieldset>
            )}
            <p className="hint -mt-2">
              {scope === "seller" ? '쿠폰 종류는 "상품 쿠폰"으로 고정됩니다. 장바구니 쿠폰은 플랫폼만 발급합니다.' : "장바구니 쿠폰은 대상이 주문 전체로 정해집니다."}
            </p>

            <fieldset className="flex flex-col gap-3" disabled={submitting}>
              <legend className="mb-1 text-[13px] font-bold">할인</legend>
              <div role="radiogroup" aria-label="할인 유형" className="flex gap-2">
                <Choice value="FIXED" label="정액 할인" description="원 단위로 깎아 줍니다" disabled={submitting} register={register("discountType")} />
                <Choice value="RATE" label="정률 할인" description="%로 깎아 주고 최대 금액을 정합니다" disabled={submitting} register={register("discountType")} />
              </div>
              <div className="grid grid-cols-2 gap-4">
                {discountType === "FIXED" ? (
                  <div className="field">
                    <label htmlFor="pf-amount">
                      할인 금액<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
                    </label>
                    <Suffix unit="원">
                      <input id="pf-amount" className="input" inputMode="numeric" placeholder="1,000" aria-invalid={errors.discountValue ? true : undefined} {...register("discountValue")} />
                    </Suffix>
                    {err("discountValue") ? renderErr("discountValue", "pf-amount-err") : <span className="hint">1원 이상</span>}
                  </div>
                ) : (
                  <>
                    <div className="field">
                      <label htmlFor="pf-percent">
                        할인율<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
                      </label>
                      <Suffix unit="%">
                        <input id="pf-percent" className="input" inputMode="numeric" placeholder="10" aria-invalid={errors.percent ? true : undefined} {...register("percent")} />
                      </Suffix>
                      {err("percent") ? renderErr("percent", "pf-percent-err") : <span className="hint">1~100의 정수로 입력합니다 (소수 불가)</span>}
                    </div>
                    <div className="field">
                      <label htmlFor="pf-max">
                        최대 할인 금액<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
                      </label>
                      <Suffix unit="원">
                        <input id="pf-max" className="input" inputMode="numeric" placeholder="5,000" aria-invalid={errors.maxDiscount ? true : undefined} {...register("maxDiscount")} />
                      </Suffix>
                      {err("maxDiscount") ? renderErr("maxDiscount", "pf-max-err") : <span className="hint">정률 할인에서는 꼭 입력합니다</span>}
                    </div>
                  </>
                )}
              </div>
            </fieldset>

            <fieldset className="flex flex-col gap-3" disabled={submitting}>
              <legend className="mb-1 text-[13px] font-bold">사용 조건 · 수량</legend>
              <div className="grid grid-cols-3 gap-4">
                <div className="field">
                  <label htmlFor="pf-min">
                    최소 주문 금액 <span className="font-normal text-text-2">(선택)</span>
                  </label>
                  <Suffix unit="원">
                    <input id="pf-min" className="input" inputMode="numeric" placeholder="비우면 조건 없음" aria-invalid={errors.minOrderAmount ? true : undefined} {...register("minOrderAmount")} />
                  </Suffix>
                  {renderErr("minOrderAmount", "pf-min-err")}
                </div>
                <div className="field">
                  <label htmlFor="pf-qty">
                    총 수량 <span className="font-normal text-text-2">(선택)</span>
                  </label>
                  <Suffix unit="장">
                    <input id="pf-qty" className="input" inputMode="numeric" placeholder="비우면 무제한" aria-invalid={errors.totalQuantity ? true : undefined} {...register("totalQuantity")} />
                  </Suffix>
                  {renderErr("totalQuantity", "pf-qty-err")}
                </div>
                <div className="field">
                  <label htmlFor="pf-days">
                    유효 일수 <span className="font-normal text-text-2">(선택)</span>
                  </label>
                  <Suffix unit="일">
                    <input id="pf-days" className="input" inputMode="numeric" placeholder="비우면 종료일까지" aria-invalid={errors.validityDays ? true : undefined} {...register("validityDays")} />
                  </Suffix>
                  {renderErr("validityDays", "pf-days-err")}
                </div>
              </div>
            </fieldset>

            <fieldset className="flex flex-col gap-3" disabled={submitting}>
              <legend className="mb-1 text-[13px] font-bold">
                발급 기간 <span className="font-normal text-text-2">(한국 시간)</span>
              </legend>
              <div className="grid grid-cols-2 gap-4">
                <div className="field">
                  <label htmlFor="pf-from">
                    시작<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
                  </label>
                  <input id="pf-from" type="datetime-local" className="input" aria-invalid={errors.validFrom ? true : undefined} {...register("validFrom")} />
                  {renderErr("validFrom", "pf-from-err")}
                </div>
                <div className="field">
                  <label htmlFor="pf-until">
                    종료<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
                  </label>
                  <input id="pf-until" type="datetime-local" className="input" aria-invalid={errors.validUntil ? true : undefined} {...register("validUntil")} />
                  {renderErr("validUntil", "pf-until-err")}
                </div>
              </div>
            </fieldset>

            <div className="flex flex-col gap-2">
              {notice.map((line) => (
                <div key={line} className="notice">
                  <span>{line}</span>
                </div>
              ))}
            </div>

            <div className="flex justify-end gap-2">
              <button type="button" className="btn btn-ghost" disabled={submitting} onClick={onClose}>
                취소
              </button>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                쿠폰 등록
              </button>
            </div>
          </form>
        </FormProvider>
      </DialogContent>
    </Dialog>
  );
}
