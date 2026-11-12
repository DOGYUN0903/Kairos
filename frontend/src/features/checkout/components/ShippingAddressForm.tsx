"use client";

import type { UseFormReturn } from "react-hook-form";
import { useState } from "react";
import { toast } from "@/hooks/use-toast";
import { useRecentShippingAddress } from "@/features/checkout/api";
import { openPostcode } from "@/features/checkout/postcode";
import type { ShippingAddressInput, ShippingAddressValues } from "@/features/checkout/schemas";

type Props = { form: UseFormReturn<ShippingAddressInput, unknown, ShippingAddressValues> };

const Req = () => (
  <span className="ml-0.5 text-danger" aria-hidden="true">
    *
  </span>
);

function Err({ id, message }: { id: string; message?: string }) {
  return message ? (
    <span id={id} className="field-error">
      {message}
    </span>
  ) : null;
}

export function ShippingAddressForm({ form }: Props) {
  const { register, setValue, setFocus, watch, formState } = form;
  const { errors } = formState;
  const recent = useRecentShippingAddress();
  const [noRecent, setNoRecent] = useState(false);
  const [searching, setSearching] = useState(false);
  const memoLength = (watch("memo") ?? "").length;

  const fill = (key: keyof ShippingAddressInput, value: string) => setValue(key, value, { shouldDirty: true, shouldValidate: true });

  const search = async () => {
    if (searching) return;
    setSearching(true);
    try {
      await openPostcode((r) => {
        fill("zipCode", r.zipCode);
        fill("address1", r.address1);
        setFocus("address2");
      });
    } catch {
      toast({ variant: "destructive", title: "주소 검색을 불러오지 못했습니다. 직접 입력해 주세요" });
    } finally {
      setSearching(false);
    }
  };

  const loadRecent = async () => {
    try {
      const a = await recent.mutateAsync();
      if (!a) {
        setNoRecent(true);
        return;
      }
      fill("receiverName", a.receiverName);
      fill("phone", a.phone);
      fill("zipCode", a.zipCode);
      fill("address1", a.address1);
      fill("address2", a.address2);
      fill("memo", a.memo ?? "");
    } catch {
      toast({ variant: "destructive", title: "최근 배송지를 불러오지 못했습니다." });
    }
  };

  const inv = (name: keyof ShippingAddressInput) => ({
    "aria-invalid": errors[name] ? (true as const) : undefined,
    "aria-describedby": errors[name] ? `co-${name}-err` : undefined,
  });

  return (
    <section className="card" aria-labelledby="addr-h">
      <div className="flex items-center justify-between border-b border-border px-6 py-4">
        <h2 id="addr-h" className="text-base font-bold">
          배송지
        </h2>
        {!noRecent && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => void loadRecent()} disabled={recent.isPending}>
            {recent.isPending ? "불러오는 중…" : "최근 배송지 불러오기"}
          </button>
        )}
      </div>
      <div className="grid grid-cols-2 gap-x-4 gap-y-4 p-6">
        <div className="field">
          <label htmlFor="co-receiverName">
            수령인
            <Req />
          </label>
          <input id="co-receiverName" className="input" type="text" maxLength={50} autoComplete="name" aria-required {...inv("receiverName")} {...register("receiverName")} />
          <Err id="co-receiverName-err" message={errors.receiverName?.message} />
        </div>
        <div className="field">
          <label htmlFor="co-phone">
            연락처
            <Req />
          </label>
          <input id="co-phone" className="input" type="tel" maxLength={20} placeholder="010-0000-0000" autoComplete="tel" aria-required {...inv("phone")} {...register("phone")} />
          <Err id="co-phone-err" message={errors.phone?.message} />
        </div>
        <div className="field col-span-2">
          <label htmlFor="co-zipCode">
            우편번호
            <Req />
          </label>
          <div className="flex max-w-[320px] gap-2">
            <input id="co-zipCode" className="input" type="text" maxLength={10} autoComplete="postal-code" aria-required {...inv("zipCode")} {...register("zipCode")} />
            <button type="button" className="btn" onClick={() => void search()} disabled={searching}>
              주소 검색
            </button>
          </div>
          <Err id="co-zipCode-err" message={errors.zipCode?.message} />
        </div>
        <div className="field col-span-2">
          <label htmlFor="co-address1">
            주소
            <Req />
          </label>
          <input id="co-address1" className="input" type="text" maxLength={200} autoComplete="address-line1" aria-required {...inv("address1")} {...register("address1")} />
          <Err id="co-address1-err" message={errors.address1?.message} />
        </div>
        <div className="field col-span-2">
          <label htmlFor="co-address2">
            상세 주소
            <Req />
          </label>
          <input id="co-address2" className="input" type="text" maxLength={200} autoComplete="address-line2" aria-required {...inv("address2")} {...register("address2")} />
          <Err id="co-address2-err" message={errors.address2?.message} />
          <span className="hint">주소 검색을 쓸 수 없을 때는 우편번호와 주소를 직접 입력할 수 있습니다.</span>
        </div>
        <div className="field col-span-2">
          <label htmlFor="co-memo">
            배송 메모 <span className="font-normal text-text-2">(선택)</span>
          </label>
          <input id="co-memo" className="input" type="text" maxLength={200} {...inv("memo")} {...register("memo")} />
          <Err id="co-memo-err" message={errors.memo?.message} />
          <span className="text-right text-xs text-text-3">{memoLength} / 200</span>
        </div>
      </div>
    </section>
  );
}
