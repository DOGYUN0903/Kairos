import type { CancelPreview, OrderDetail, OrderItemResult, SellerOrderResult } from "@/features/orders/api";
import { canPay } from "@/features/payment/pay-state";

// 결제창을 열어도 되는가의 기준은 결제 페이지와 같은 한 곳(payment/pay-state)에 둔다.
export { canPay };

/** 주문 PAID + 판매자 주문 PAID(발송 전) + 항목 ORDERED 일 때만 취소 버튼을 둔다. */
export function canCancelItem(order: OrderDetail, sellerOrder: SellerOrderResult, item: OrderItemResult): boolean {
  return order.status === "PAID" && sellerOrder.status === "PAID" && item.status === "ORDERED";
}

/** 구매 확정 버튼을 보일지. 열린 반품이 있어도 숨기지 않는다(비활성과 이유는 confirmBlockedReason). */
export function canConfirmPurchase(sellerOrder: SellerOrderResult): boolean {
  return sellerOrder.status === "DELIVERED";
}

export const OPEN_RETURN_BLOCK_REASON = "반품이 진행 중인 상품이 있어 확정할 수 없습니다";

/** 구매 확정을 막는 이유(없으면 null). 열린 반품이 있으면 서버가 409 로 거절한다. */
export function confirmBlockedReason(sellerOrder: Pick<SellerOrderResult, "status" | "hasOpenReturn">): string | null {
  return sellerOrder.hasOpenReturn ? OPEN_RETURN_BLOCK_REASON : null;
}

/** 판매자 주문 칩의 상태 키(구매자 주문 상세용). 모든 항목이 반품되면 "전체 반품". */
export function sellerOrderChip(sellerOrder: Pick<SellerOrderResult, "status" | "allItemsReturned">): string {
  return sellerOrder.allItemsReturned ? "ALL_RETURNED" : sellerOrder.status;
}

function paymentChecking(order: OrderDetail): boolean {
  const payment = order.payment?.status;
  return payment === "IN_PROGRESS" || payment === "UNKNOWN";
}

export type OrderHeaderKind = "pending" | "checking" | "paid" | "completed" | "canceled";

/** 상태 헤더 종류. 결제가 확인 중이면 주문 상태보다 우선한다(결과를 단정하지 않는다). */
export function orderHeader(order: OrderDetail): OrderHeaderKind {
  // 시각을 받지 않는다: 기한이 지나도 서버가 취소할 때까지는 결제 대기 헤더를 유지한다(시각으로 상태를 추측하지 않고 서버가 정한다).
  if (order.status === "CANCELED") return "canceled";
  if (order.status === "COMPLETED") return "completed";
  if (paymentChecking(order)) return "checking";
  return order.status === "PENDING_PAYMENT" ? "pending" : "paid";
}

/** 환불 미리보기 내역. 항목 금액은 항상, 나머지는 0이 아닐 때만. 값은 모두 서버 값이다. */
export function refundLines(preview: CancelPreview): { label: string; amount: number; negative: boolean }[] {
  const lines = [{ label: "항목 금액", amount: preview.paidAmount, negative: false }];
  if (preview.shippingFeeRefund !== 0) lines.push({ label: "배송비 환불", amount: preview.shippingFeeRefund, negative: false });
  if (preview.shippingFeeDeducted !== 0) lines.push({ label: "배송비 차감", amount: preview.shippingFeeDeducted, negative: true });
  if (preview.shippingFeeReturned !== 0) lines.push({ label: "차감 배송비 반환", amount: preview.shippingFeeReturned, negative: false });
  return lines;
}

/** 남은 결제 금액 표시 여부. remainingAmount는 실제로 결제된 주문에서만 의미가 있다(결제 전 취소·결제 대기에서는 숨긴다). */
export function showsRemainingAmount(order: OrderDetail): boolean {
  return order.paidAt != null;
}
