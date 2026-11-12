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

export type SettlementRowKey = "sales" | "sellerCoupon" | "commission" | "shipping" | "returnFee" | "adjustment" | "total" | "payable";

/** key 는 줄의 고정 식별자, sign 은 시안의 줄 앞 기호(표시용)다. */
export type SettlementRow = { key: SettlementRowKey; label: string; text: string; sign: "" | "−" | "+" | "±" | "=" };

/** 계산 내역 8줄(순서 고정, 0원도 숨기지 않는다). 값은 서버 필드 그대로이고 표시 문자(부호)만 정한다. 부호 반전은 표시용이다. */
export function settlementRows(d: Amounts): SettlementRow[] {
  return [
    { key: "sales", label: "상품 매출", text: formatKrwMinus(d.salesAmount), sign: "" },
    { key: "sellerCoupon", label: "판매자 쿠폰 할인", text: formatSignedKrw(-d.sellerCouponDiscount), sign: "−" },
    { key: "commission", label: "수수료", text: formatSignedKrw(-d.commissionAmount), sign: "−" },
    { key: "shipping", label: "배송비", text: formatSignedKrw(d.shippingFeeOwed), sign: "+" },
    { key: "returnFee", label: "판매자 부담 반품비", text: formatSignedKrw(-d.returnFeeBorne), sign: "−" },
    { key: "adjustment", label: "조정", text: formatSignedKrw(d.adjustmentAmount), sign: "±" },
    { key: "total", label: "합계", text: formatKrwMinus(d.totalAmount), sign: "=" },
    { key: "payable", label: "지급액", text: formatKrwMinus(d.payableAmount), sign: "" },
  ];
}

/** 합계가 음수면 지급 없이 다음 정산으로 이월된다. */
export function isCarriedOver(d: { totalAmount: number }): boolean {
  return d.totalAmount < 0;
}
