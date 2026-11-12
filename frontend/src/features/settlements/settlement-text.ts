import { formatKrwMinus, formatSignedKrw } from "@/lib/money";

export function settlementStatusLabel(s: "GENERATED" | "CONFIRMED" | "PAID"): string {
  return s === "GENERATED" ? "정산 생성" : s === "CONFIRMED" ? "확정" : "지급 완료";
}

export function adjustmentTypeLabel(t: "MANUAL" | "CARRY_OVER"): string {
  return t === "MANUAL" ? "수동 조정" : "이월";
}

type Amounts = {
  salesAmount: number;
  sellerCouponDiscount: number;
  commissionAmount: number;
  shippingFeeOwed: number;
  returnFeeBorne: number;
  adjustmentAmount: number;
  totalAmount: number;
  payableAmount: number;
};

/** 계산 내역 8줄. 값은 서버 필드 그대로이고 표시 문자(부호)만 정한다. 부호 반전은 표시용이다. */
export function settlementRows(d: Amounts): { label: string; text: string }[] {
  return [
    { label: "상품 매출", text: formatKrwMinus(d.salesAmount) },
    { label: "판매자 쿠폰 할인", text: formatSignedKrw(-d.sellerCouponDiscount) },
    { label: "수수료", text: formatSignedKrw(-d.commissionAmount) },
    { label: "배송비", text: formatSignedKrw(d.shippingFeeOwed) },
    { label: "판매자 부담 반품비", text: formatSignedKrw(-d.returnFeeBorne) },
    { label: "조정", text: formatSignedKrw(d.adjustmentAmount) },
    { label: "합계", text: formatKrwMinus(d.totalAmount) },
    { label: "지급액", text: formatKrwMinus(d.payableAmount) },
  ];
}

/** 합계가 음수면 지급 없이 다음 정산으로 이월된다. */
export function isCarriedOver(d: { totalAmount: number }): boolean {
  return d.totalAmount < 0;
}
