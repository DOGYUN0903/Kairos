import type { OrderItemResult } from "@/features/orders/api";
import type { SellerOrderDetail } from "@/features/seller/orders/api";

/** 발송: 판매자 주문 PAID, 취소 확인 중 항목이 없고, 보낼 항목(ORDERED)이 하나 이상. */
export function canShip(so: SellerOrderDetail): boolean {
  return so.status === "PAID" && !so.items.some((i) => i.status === "CANCEL_REQUESTED") && so.items.some((i) => i.status === "ORDERED");
}

export function canDeliver(so: SellerOrderDetail): boolean {
  return so.status === "SHIPPING";
}

/** 항목 취소: 배송 전(판매자 주문 PAID)의 ORDERED 항목만. */
export function canSellerCancelItem(so: SellerOrderDetail, item: OrderItemResult): boolean {
  return so.status === "PAID" && item.status === "ORDERED";
}

/** 발송 처리를 막는 이유(버튼 안내용). 막히지 않았으면 null. */
export function shipBlockReason(so: SellerOrderDetail): string | null {
  if (so.status !== "PAID") return null;
  if (so.items.some((i) => i.status === "CANCEL_REQUESTED")) return "취소 확인 중인 상품이 있습니다";
  if (!so.items.some((i) => i.status === "ORDERED")) return "발송할 상품이 없습니다";
  return null;
}
