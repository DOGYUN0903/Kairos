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

/** 계산 내역 8줄. sign 은 시안의 줄 앞 기호(표시용)이고 값은 서버 필드 그대로이고 표시 문자(부호)만 정한다. 부호 반전은 표시용이다. */
export type SettlementRow = { label: string; text: string; sign: "" | "−" | "+" | "±" | "=" };

export function settlementRows(d: Amounts): SettlementRow[] {
  return [
    { label: "상품 매출", text: formatKrwMinus(d.salesAmount), sign: "" },
    { label: "판매자 쿠폰 할인", text: formatSignedKrw(-d.sellerCouponDiscount), sign: "−" },
    { label: "수수료", text: formatSignedKrw(-d.commissionAmount), sign: "−" },
    { label: "배송비", text: formatSignedKrw(d.shippingFeeOwed), sign: "+" },
    { label: "판매자 부담 반품비", text: formatSignedKrw(-d.returnFeeBorne), sign: "−" },
    { label: "조정", text: formatSignedKrw(d.adjustmentAmount), sign: "±" },
    { label: "합계", text: formatKrwMinus(d.totalAmount), sign: "=" },
    { label: "지급액", text: formatKrwMinus(d.payableAmount), sign: "" },
  ];
}

/** 합계가 음수면 지급 없이 다음 정산으로 이월된다. */
export function isCarriedOver(d: { totalAmount: number }): boolean {
  return d.totalAmount < 0;
}
