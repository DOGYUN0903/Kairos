import type { OrderDetail } from "@/features/orders/api";
import { secondsUntilKst } from "@/lib/datetime";

/** 결제창을 열어도 되는가: 결제 대기이고, 기한이 남았고, 결제가 진행 중·결과 미확정이 아니다. */
export function canPay(order: OrderDetail, nowMs: number): boolean {
  if (order.status !== "PENDING_PAYMENT") return false;
  // 0원 주문은 PG 를 거치지 않는다(결제창을 열 금액이 없다).
  if (order.totalAmount <= 0) return false;
  if (secondsUntilKst(order.expiresAt, nowMs) === 0) return false;
  const payment = order.payment?.status;
  return payment !== "IN_PROGRESS" && payment !== "UNKNOWN";
}

export function isPaid(order: OrderDetail): boolean {
  return order.status === "PAID" || order.status === "COMPLETED";
}

/** 결제창에 보이는 주문명: "첫 상품명 외 n건". */
export function orderNameOf(order: OrderDetail): string {
  const items = order.sellerOrders.flatMap((so) => so.items);
  const first = items[0]?.productName ?? "주문 상품";
  return items.length > 1 ? `${first} 외 ${items.length - 1}건` : first;
}
