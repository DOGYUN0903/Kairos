import { isPastKst } from "@/lib/datetime";

export type ReturnEntry = { kind: "request"; until: string } | { kind: "expired" } | { kind: "blocked"; text: string } | { kind: "none" };

/** 주문 항목에서 반품 요청을 낼 수 있는지·왜 못 내는지. 판단은 서버 값(`returnableUntil`)과 현재 시각만으로 한다. */
export function returnEntry(
  input: { sellerOrderStatus: string; itemStatus: string; returnableUntil: string | null | undefined; returnStatus: string | null | undefined },
  nowMs: number,
): ReturnEntry {
  if (input.sellerOrderStatus !== "DELIVERED" || input.itemStatus !== "ORDERED") return { kind: "none" };
  const rs = input.returnStatus;
  if (rs === "REJECTED") return { kind: "blocked", text: "반품이 거절된 상품입니다" };
  if (rs === "CANCELED_BY_ADMIN") return { kind: "blocked", text: "반품이 중단된 상품입니다" };
  if (rs != null && rs !== "WITHDRAWN") return { kind: "none" };
  const until = input.returnableUntil;
  if (!until) return { kind: "none" };
  return isPastKst(until, nowMs) ? { kind: "expired" } : { kind: "request", until };
}

export function canWithdraw(returnStatus: string | null | undefined): boolean {
  return returnStatus === "REQUESTED";
}

export type RefundRow = { key: string; label: string; amount: number; sign: "plus" | "minus" | "none"; unit: "won" | "point" };

/** 환불 내역 줄. 0 이 아닌 줄만 순서대로(결제 금액·환불 금액은 항상). 금액은 서버 값 그대로이고 부호만 정한다. */
export function refundRows(
  r: {
    paidAmount: number;
    returnFee: number;
    outboundFeeRecovered: number;
    shippingFeeRefund: number;
    refundAmount: number;
    pointsRefund: number;
  },
  refundLabel: "환불 예정 금액" | "환불 금액" = "환불 금액",
): RefundRow[] {
  const rows: RefundRow[] = [{ key: "paid", label: "결제 금액", amount: r.paidAmount, sign: "none", unit: "won" }];
  if (r.returnFee !== 0) rows.push({ key: "returnFee", label: "반품비", amount: r.returnFee, sign: "minus", unit: "won" });
  if (r.outboundFeeRecovered !== 0) rows.push({ key: "outbound", label: "발송 배송비 회수", amount: r.outboundFeeRecovered, sign: "minus", unit: "won" });
  if (r.shippingFeeRefund !== 0) rows.push({ key: "shippingRefund", label: "배송비 환불", amount: r.shippingFeeRefund, sign: "plus", unit: "won" });
  rows.push({ key: "refund", label: refundLabel, amount: r.refundAmount, sign: "none", unit: "won" });
  if (r.pointsRefund !== 0) rows.push({ key: "points", label: "포인트 반환", amount: r.pointsRefund, sign: "none", unit: "point" });
  return rows;
}
