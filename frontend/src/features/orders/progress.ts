import type { OrderSummary } from "@/features/orders/api";

type OrderStatus = OrderSummary["status"];
type SellerOrderStatus = OrderSummary["sellerOrderStatuses"][number];

const ORDER: { status: SellerOrderStatus; label: string; short: string }[] = [
  { status: "SHIPPING", label: "배송 중", short: "배송 중" },
  { status: "PAID", label: "배송 준비 중", short: "배송 준비" },
  { status: "DELIVERED", label: "배송 완료", short: "배송 완료" },
  { status: "CONFIRMED", label: "구매 확정", short: "구매 확정" },
];

/** 주문 카드의 진행 상태 문구. 주문 상태와 판매자 주문 상태 묶음으로만 만든다. */
export function orderProgressLabel(status: OrderStatus, sellerOrderStatuses: SellerOrderStatus[]): string {
  if (status === "PENDING_PAYMENT") return "결제 대기";
  if (status === "CANCELED") return "취소";
  if (status === "COMPLETED") return "구매 확정";

  const counts = ORDER.map((o) => ({ ...o, n: sellerOrderStatuses.filter((s) => s === o.status).length })).filter((o) => o.n > 0);
  if (counts.length === 0) return "결제 완료";
  const only = counts[0];
  if (counts.length === 1 && only) return only.label;
  return counts.map((o) => `${o.short} ${o.n}`).join(" · ");
}
