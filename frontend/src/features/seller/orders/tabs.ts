import type { SellerOrderStatus } from "@/features/seller/orders/api";

export const SELLER_ORDER_TABS: readonly { status: SellerOrderStatus | undefined; label: string }[] = [
  { status: undefined, label: "전체" },
  { status: "PAID", label: "발송 대기" },
  { status: "SHIPPING", label: "배송 중" },
  { status: "DELIVERED", label: "배송 완료" },
  { status: "CONFIRMED", label: "구매 확정" },
  { status: "CANCELED", label: "취소" },
];

/** URL 이 진실이다. 모르는 status(PENDING_PAYMENT 포함)는 전체, 음수·비숫자 page 는 0. */
export function parseSellerOrderParams(sp: { get(name: string): string | null }): { status?: SellerOrderStatus; page: number } {
  const raw = sp.get("status");
  const tab = SELLER_ORDER_TABS.find((t) => t.status !== undefined && t.status === raw);
  const rawPage = sp.get("page");
  const page = rawPage !== null && /^\d{1,9}$/.test(rawPage) ? Number(rawPage) : 0;
  return tab?.status ? { status: tab.status, page } : { page };
}
