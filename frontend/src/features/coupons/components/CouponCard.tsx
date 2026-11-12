import type { ReactNode } from "react";
import { conditionLabel, discountLabel, type DiscountTerms } from "@/features/coupons/discount-text";
import { formatBp, formatKrw } from "@/lib/money";
import { cn } from "@/lib/utils";

type Issuer = "PLATFORM" | "SELLER";
type CouponKind = "PRODUCT" | "CART";

const badge = "inline-flex h-5 items-center rounded-[2px] px-1.5 text-[11px] font-bold leading-none";

export function IssuerBadge({ issuerType }: { issuerType: Issuer }) {
  return issuerType === "PLATFORM" ? (
    <span className={cn(badge, "bg-navy text-white")}>플랫폼 쿠폰</span>
  ) : (
    <span className={cn(badge, "bg-[#E8EDF7] text-navy")}>판매자 쿠폰</span>
  );
}

export function KindBadge({ couponType }: { couponType: CouponKind }) {
  return <span className={cn(badge, "border border-border")}>{couponType === "PRODUCT" ? "상품 쿠폰" : "장바구니 쿠폰"}</span>;
}

type Props = {
  name: string;
  terms: DiscountTerms;
  /** 발급 주체·종류·곧 만료 같은 배지들. */
  tags: ReactNode;
  /** 기간 줄 등. 조건 문구 뒤에 " · " 로 이어 붙는다. */
  period: ReactNode;
  /** 상품 하나 대상일 때의 대상 상품 이름. */
  targetProductName?: string | null;
  action?: ReactNode;
  small?: boolean;
  done?: boolean;
};

/** 쿠폰 한 장(시안 .coupon). 받을 수 있는 쿠폰과 내 쿠폰이 함께 쓴다. */
export function CouponCard({ name, terms, tags, period, targetProductName, action, small, done }: Props) {
  const condition = conditionLabel(terms) ?? "최소 주문 금액 없음";
  return (
    <div className={cn("relative grid overflow-hidden rounded-lg border border-border bg-bg", small ? "grid-cols-[96px_minmax(0,1fr)_auto]" : "grid-cols-[112px_minmax(0,1fr)_auto]")}>
      <div className="relative flex flex-col items-center justify-center gap-0.5 border-r border-dashed border-border bg-surface px-2.5 py-4 text-center">
        <span className="sr-only">{discountLabel(terms)}</span>
        <strong aria-hidden className={cn("font-black leading-tight tracking-tight tabular-nums", small ? "text-[17px]" : "text-[22px]", done ? "text-text-3" : "text-navy")}>
          {terms.discountType === "FIXED" ? formatKrw(terms.discountValue) : formatBp(terms.discountValue)}
        </strong>
        {terms.discountType === "RATE" && terms.maxDiscount ? (
          <small aria-hidden className="whitespace-nowrap text-[11px] text-text-2">
            최대 {formatKrw(terms.maxDiscount)}
          </small>
        ) : null}
      </div>
      <div className={cn("flex min-w-0 flex-col gap-1", small ? "px-3.5 py-3" : "px-4 py-3.5")}>
        <div className="flex flex-wrap gap-1">{tags}</div>
        <div className={cn("text-sm font-bold", done && "text-text-2")}>{name}</div>
        <div className="text-xs text-text-2">
          {targetProductName !== undefined && (
            <>
              {targetProductName}
              <br />
            </>
          )}
          {condition} · {period}
        </div>
      </div>
      {action && <div className="flex flex-col items-end justify-center gap-1.5 px-4">{action}</div>}
    </div>
  );
}
