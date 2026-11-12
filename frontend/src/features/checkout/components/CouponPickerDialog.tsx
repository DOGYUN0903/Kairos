"use client";

import { useState, type ReactNode } from "react";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Money } from "@/components/common/Money";
import type { QuoteCoupon } from "@/features/checkout/api";
import { CouponBadges } from "@/features/checkout/components/CouponBadges";
import { discountLabel, conditionLabel } from "@/features/coupons/discount-text";
import { formatDate } from "@/lib/datetime";

type Props = {
  title: string;
  /** 창 위쪽에 보이는 대상(상품 줄 요약 등). */
  subject?: ReactNode;
  scope: "상품 쿠폰" | "장바구니 쿠폰";
  /** 견적이 준 쿠폰 목록(순서·usable·expectedDiscount 모두 서버 값). */
  coupons: QuoteCoupon[];
  selectedId: number | null;
  /** 다른 상품에 이미 적용된 쿠폰인가(고르면 이쪽으로 옮겨진다). 장바구니 쿠폰에는 쓰지 않는다. */
  appliedElsewhere?: (couponId: number) => boolean;
  onApply: (couponId: number | null) => void;
  onClose: () => void;
};

function unusableText(c: QuoteCoupon): string {
  if (c.unusableReason === "NOTHING_TO_DISCOUNT") return "할인할 금액이 없습니다";
  const cond = conditionLabel(c);
  return cond ? `${cond} 사용 가능` : "사용할 수 없는 쿠폰입니다";
}

/** 쿠폰 선택 창(라디오 목록). 고른 뒤 "적용하기"를 눌러야 선택이 바뀐다. */
export function CouponPickerDialog({ title, subject, scope, coupons, selectedId, appliedElsewhere, onApply, onClose }: Props) {
  const [draft, setDraft] = useState<number | null>(selectedId);
  const usable = coupons.filter((c) => c.usable);
  const unusable = coupons.filter((c) => !c.usable);
  const name = "coupon-choice";

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-[520px]">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription className="sr-only">{scope === "장바구니 쿠폰" ? "주문에 한 장만 사용할 수 있습니다." : "한 상품에 한 장만 사용할 수 있습니다."}</DialogDescription>
        </DialogHeader>
        {subject}
        <div role="radiogroup" aria-label={title} className="flex max-h-[440px] flex-col gap-3 overflow-auto">
          <div className="text-[13px] font-bold">
            사용할 수 있는 쿠폰<span className="ml-1 font-medium text-text-2">{usable.length}</span>
          </div>
          {usable.map((c) => (
            <CouponOption key={c.couponId} coupon={c} scope={scope} name={name} checked={draft === c.couponId} elsewhere={appliedElsewhere?.(c.couponId) ?? false} onChange={() => setDraft(c.couponId)} />
          ))}
          {unusable.length > 0 && (
            <div className="mt-2 text-[13px] font-bold">
              사용할 수 없는 쿠폰<span className="ml-1 font-medium text-text-2">{unusable.length}</span>
            </div>
          )}
          {unusable.map((c) => (
            <CouponOption key={c.couponId} coupon={c} scope={scope} name={name} checked={false} elsewhere={false} onChange={() => {}} />
          ))}
          <label className={`flex min-h-[52px] items-center gap-2.5 rounded-lg border px-4 font-medium ${draft === null ? "border-navy shadow-[0_0_0_1px_#1b2a4a]" : "border-border"}`}>
            <input type="radio" name={name} className="h-[18px] w-[18px] accent-[#1b2a4a]" checked={draft === null} onChange={() => setDraft(null)} />
            적용 안 함
          </label>
        </div>
        {appliedElsewhere && <p className="-mt-2 text-xs text-text-2">&quot;다른 상품에 적용됨&quot; 쿠폰을 고르면 이 상품으로 옮겨지고, 이전 상품에서는 해제됩니다.</p>}
        <div className="grid grid-cols-2 gap-2">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            취소
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              onApply(draft);
              onClose();
            }}
          >
            적용하기
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function CouponOption({ coupon: c, scope, name, checked, elsewhere, onChange }: { coupon: QuoteCoupon; scope: Props["scope"]; name: string; checked: boolean; elsewhere: boolean; onChange: () => void }) {
  const cond = conditionLabel(c);
  const disabled = !c.usable;
  return (
    <label className={`grid grid-cols-[96px_minmax(0,1fr)_auto] overflow-hidden rounded-lg border bg-white ${checked ? "border-navy shadow-[0_0_0_1px_#1b2a4a]" : "border-border"} ${disabled ? "cursor-not-allowed" : "cursor-pointer"}`}>
      <div className={`flex flex-col items-center justify-center gap-0.5 border-r border-dashed border-border bg-surface px-2.5 py-3 text-center ${disabled ? "opacity-50" : ""}`}>
        <strong className="text-[13px] font-black leading-snug text-navy">{discountLabel(c)}</strong>
      </div>
      <div className={`flex min-w-0 flex-col gap-1 px-3.5 py-3 ${disabled ? "opacity-50" : ""}`}>
        <div className="flex flex-wrap gap-1">
          <CouponBadges coupon={c} scope={scope} />
          {elsewhere && <span className="inline-flex h-5 items-center rounded-sm bg-[#E8EDF7] px-1.5 text-[11px] font-bold leading-none text-navy">다른 상품에 적용됨</span>}
        </div>
        <div className="text-sm font-bold">{c.name}</div>
        <div className="text-xs text-text-2">
          {cond ?? "최소 주문 금액 없음"} · {formatDate(c.expiresAt)}까지
        </div>
      </div>
      {/* 쓸 수 없는 쿠폰의 이유는 흐리게 하지 않는다. */}
      <div className="flex flex-col items-end justify-center gap-1.5 px-4">
        {!disabled && c.expectedDiscount != null && <span className="text-[15px] font-bold tabular-nums text-danger">−<Money value={c.expectedDiscount} /></span>}
        <input type="radio" name={name} aria-label={c.name} className="h-[18px] w-[18px] accent-[#1b2a4a]" checked={checked} disabled={disabled} onChange={onChange} />
      </div>
      {disabled && <div className="col-span-3 border-t border-border px-3.5 py-2 text-xs font-medium text-danger">{unusableText(c)}</div>}
    </label>
  );
}
