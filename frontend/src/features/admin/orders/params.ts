import type { OrderStatus } from "@/features/admin/orders/api";
import { parsePage } from "@/lib/page";

export const ADMIN_ORDER_STATUSES: readonly { value: OrderStatus; label: string }[] = [
  { value: "PENDING_PAYMENT", label: "결제 대기" },
  { value: "PAID", label: "결제 완료" },
  { value: "COMPLETED", label: "구매 확정" },
  { value: "CANCELED", label: "취소" },
];

export const ORDER_NO_MAX = 50;

export type AdminOrderParams = { status?: OrderStatus; memberId?: number; orderNo?: string; page: number };

/** URL 이 필터의 진실이다. 잘못된 status·memberId·빈 orderNo 는 조용히 무시한다. */
export function parseAdminOrderParams(sp: { get(name: string): string | null }): AdminOrderParams {
  const rawStatus = sp.get("status");
  const status = ADMIN_ORDER_STATUSES.find((s) => s.value === rawStatus)?.value;
  const rawMember = sp.get("memberId");
  const member = rawMember !== null && /^[1-9]\d{0,14}$/.test(rawMember) ? Number(rawMember) : undefined;
  const orderNo = (sp.get("orderNo") ?? "").trim().slice(0, ORDER_NO_MAX);
  const page = parsePage(sp.get("page"));
  return {
    ...(status ? { status } : {}),
    ...(member !== undefined ? { memberId: member } : {}),
    ...(orderNo ? { orderNo } : {}),
    page,
  };
}

/** 기본값은 싣지 않는다. 앞의 `?` 는 붙이지 않는다. */
export function toAdminOrderSearch(p: AdminOrderParams): string {
  const q = new URLSearchParams();
  if (p.status) q.set("status", p.status);
  if (p.memberId !== undefined) q.set("memberId", String(p.memberId));
  if (p.orderNo) q.set("orderNo", p.orderNo);
  if (p.page > 0) q.set("page", String(p.page));
  return q.toString();
}
