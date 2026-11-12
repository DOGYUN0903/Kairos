import type { ReturnRequestResponse } from "@/features/returns/api";
import { RETURN_FILTER_LABEL } from "@/features/returns/return-text";

export type ReturnTabStatus = Extract<ReturnRequestResponse["status"], "REQUESTED" | "APPROVED" | "REFUNDING" | "COMPLETED" | "REJECTED">;

export const RETURN_TABS: readonly { status: ReturnTabStatus | undefined; label: string }[] = [
  { status: undefined, label: "전체" },
  { status: "REQUESTED", label: RETURN_FILTER_LABEL.REQUESTED },
  { status: "APPROVED", label: RETURN_FILTER_LABEL.APPROVED },
  { status: "REFUNDING", label: RETURN_FILTER_LABEL.REFUNDING },
  { status: "COMPLETED", label: RETURN_FILTER_LABEL.COMPLETED },
  { status: "REJECTED", label: RETURN_FILTER_LABEL.REJECTED },
];

/** URL(?status=)이 탭의 진실이다. 모르는 값(철회·중단 포함)은 전체(undefined). 페이지는 parsePage 로 따로 읽는다. */
export function parseReturnTab(sp: { get(name: string): string | null }): ReturnTabStatus | undefined {
  const raw = sp.get("status");
  return RETURN_TABS.find((t) => t.status !== undefined && t.status === raw)?.status;
}
