import { formatBp, formatKrw } from "@/lib/money";

export type DiscountTerms = {
  discountType: "FIXED" | "RATE";
  discountValue: number;
  maxDiscount?: number | null;
  minOrderAmount?: number | null;
};

/** 쿠폰 카드·선택 창·관리 표가 함께 쓰는 할인 문구. */
export function discountLabel(t: DiscountTerms): string {
  if (t.discountType === "FIXED") return `${formatKrw(t.discountValue)} 할인`;
  const base = `${formatBp(t.discountValue)} 할인`;
  return t.maxDiscount ? `${base} (최대 ${formatKrw(t.maxDiscount)})` : base;
}

export function conditionLabel(t: DiscountTerms): string | null {
  return t.minOrderAmount ? `${formatKrw(t.minOrderAmount)} 이상 구매 시` : null;
}
