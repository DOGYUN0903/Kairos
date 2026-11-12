import type { QuoteCoupon } from "@/features/checkout/api";

const BADGE = "inline-flex h-5 items-center rounded-sm px-1.5 text-[11px] font-bold leading-none";

/** 발급 주체 + 적용 범위 배지. */
export function CouponBadges({ coupon, scope }: { coupon: Pick<QuoteCoupon, "issuerType">; scope: "상품 쿠폰" | "장바구니 쿠폰" }) {
  return (
    <>
      <span className={`${BADGE} ${coupon.issuerType === "PLATFORM" ? "bg-navy text-white" : "bg-[#E8EDF7] text-navy"}`}>{coupon.issuerType === "PLATFORM" ? "플랫폼 쿠폰" : "판매자 쿠폰"}</span>
      <span className={`${BADGE} border border-border text-text-2`}>{scope}</span>
    </>
  );
}
