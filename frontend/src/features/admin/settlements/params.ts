import { parseId } from "@/lib/ids";
import { parsePage } from "@/lib/page";

export type SettlementStatus = "GENERATED" | "CONFIRMED" | "PAID";
export const SETTLEMENT_STATUSES: readonly { value: SettlementStatus; label: string }[] = [
  { value: "GENERATED", label: "정산 생성" },
  { value: "CONFIRMED", label: "확정" },
  { value: "PAID", label: "지급 완료" },
];

export type AdminSettlementParams = { sellerId?: number; status?: SettlementStatus; page: number };

/** URL 이 필터의 진실이다. 잘못된 sellerId·status 는 조용히 무시한다. */
export function parseAdminSettlementParams(sp: { get(name: string): string | null }): AdminSettlementParams {
  const sellerId = parseId(sp.get("sellerId"));
  const status = SETTLEMENT_STATUSES.find((s) => s.value === sp.get("status"))?.value;
  return { ...(sellerId !== null ? { sellerId } : {}), ...(status ? { status } : {}), page: parsePage(sp.get("page")) };
}

/** 기본값은 싣지 않는다. 앞의 `?` 는 붙이지 않는다. */
export function toAdminSettlementSearch(p: AdminSettlementParams): string {
  const q = new URLSearchParams();
  if (p.sellerId !== undefined) q.set("sellerId", String(p.sellerId));
  if (p.status) q.set("status", p.status);
  if (p.page > 0) q.set("page", String(p.page));
  return q.toString();
}
