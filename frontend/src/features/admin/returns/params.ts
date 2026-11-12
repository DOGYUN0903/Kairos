import type { ReturnRequestResponse } from "@/features/returns/api";
import { RETURN_FILTER_LABEL } from "@/features/returns/return-text";
import { parseId } from "@/lib/ids";
import { parsePage } from "@/lib/page";

export type AdminReturnStatus = ReturnRequestResponse["status"];

export const ADMIN_RETURN_STATUSES: readonly { value: AdminReturnStatus; label: string }[] = [
  { value: "REQUESTED", label: RETURN_FILTER_LABEL.REQUESTED },
  { value: "APPROVED", label: RETURN_FILTER_LABEL.APPROVED },
  { value: "REFUNDING", label: RETURN_FILTER_LABEL.REFUNDING },
  { value: "COMPLETED", label: RETURN_FILTER_LABEL.COMPLETED },
  { value: "REJECTED", label: RETURN_FILTER_LABEL.REJECTED },
  { value: "WITHDRAWN", label: RETURN_FILTER_LABEL.WITHDRAWN },
  { value: "CANCELED_BY_ADMIN", label: RETURN_FILTER_LABEL.CANCELED_BY_ADMIN },
];

export type AdminReturnParams = { status?: AdminReturnStatus; sellerId?: number; memberId?: number; page: number };

/** URL 이 필터의 진실이다. 모르는 status 와 양의 정수가 아닌 sellerId·memberId 는 조용히 버린다. */
export function parseAdminReturnParams(sp: { get(name: string): string | null }): AdminReturnParams {
  const rawStatus = sp.get("status");
  const status = ADMIN_RETURN_STATUSES.find((s) => s.value === rawStatus)?.value;
  const sellerId = parseId(sp.get("sellerId"));
  const memberId = parseId(sp.get("memberId"));
  return {
    ...(status ? { status } : {}),
    ...(sellerId !== null ? { sellerId } : {}),
    ...(memberId !== null ? { memberId } : {}),
    page: parsePage(sp.get("page")),
  };
}

/** 기본값은 싣지 않는다. 앞의 `?` 는 붙이지 않는다. */
export function toAdminReturnSearch(p: AdminReturnParams): string {
  const q = new URLSearchParams();
  if (p.status) q.set("status", p.status);
  if (p.sellerId !== undefined) q.set("sellerId", String(p.sellerId));
  if (p.memberId !== undefined) q.set("memberId", String(p.memberId));
  if (p.page > 0) q.set("page", String(p.page));
  return q.toString();
}

/** 상태별 관리자 작업: 요청·거절 → 승인(거절된 건 재승인), 승인 → 대신 수거 확인·중단. 그 밖은 없다. */
export function adminReturnActions(status: string): ("approve" | "receive" | "cancel")[] {
  if (status === "REQUESTED" || status === "REJECTED") return ["approve"];
  if (status === "APPROVED") return ["receive", "cancel"];
  return [];
}
