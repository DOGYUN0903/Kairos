import type { ReturnRequestResponse } from "@/features/returns/api";

export type ReturnTabStatus = Extract<ReturnRequestResponse["status"], "REQUESTED" | "APPROVED" | "REFUNDING" | "COMPLETED" | "REJECTED">;

export const RETURN_TABS: readonly { status: ReturnTabStatus | undefined; label: string }[] = [
  { status: undefined, label: "전체" },
  { status: "REQUESTED", label: "요청" },
  { status: "APPROVED", label: "승인 · 수거 대기" },
  { status: "REFUNDING", label: "환불 확인 중" },
  { status: "COMPLETED", label: "완료" },
  { status: "REJECTED", label: "거절" },
];

/** URL(?status=)이 탭의 진실이다. 모르는 값(철회·중단 포함)은 전체(undefined). 페이지는 parsePage 로 따로 읽는다. */
export function parseReturnTab(sp: { get(name: string): string | null }): ReturnTabStatus | undefined {
  const raw = sp.get("status");
  return RETURN_TABS.find((t) => t.status !== undefined && t.status === raw)?.status;
}
