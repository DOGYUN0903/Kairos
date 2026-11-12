import { formatKrw } from "@/lib/money";

type Reason = "CHANGE_OF_MIND" | "DEFECTIVE" | "WRONG_DELIVERY";
type Status = "REQUESTED" | "APPROVED" | "REFUNDING" | "COMPLETED" | "REJECTED" | "WITHDRAWN" | "CANCELED_BY_ADMIN";

const REASONS: Record<Reason, string> = { CHANGE_OF_MIND: "단순 변심", DEFECTIVE: "상품 불량", WRONG_DELIVERY: "오배송" };
const STATUSES: Record<Status, string> = {
  REQUESTED: "반품 요청됨",
  APPROVED: "반품 승인 · 수거 대기",
  REFUNDING: "환불 확인 중",
  COMPLETED: "반품 완료",
  REJECTED: "반품 거절",
  WITHDRAWN: "철회됨",
  CANCELED_BY_ADMIN: "반품 중단됨",
};

export const returnReasonLabel = (r: Reason): string => REASONS[r];
export const returnStatusLabel = (s: Status): string => STATUSES[s];

/** 필터·탭에 쓰는 짧은 상태 이름(판매자·관리자 목록 공용). */
export const RETURN_FILTER_LABEL: Record<Status, string> = {
  REQUESTED: "요청",
  APPROVED: "승인 · 수거 대기",
  REFUNDING: "환불 확인 중",
  COMPLETED: "완료",
  REJECTED: "거절",
  WITHDRAWN: "철회",
  CANCELED_BY_ADMIN: "중단",
};

export const feeBearerLabel = (b: "BUYER" | "SELLER"): string => (b === "BUYER" ? "구매자" : "판매자");

export function feeBearerNotice(b: "BUYER" | "SELLER"): string {
  return b === "BUYER" ? "반품 배송비는 구매자 부담입니다" : "판매자 부담이라 차감이 없습니다";
}

export function decidedByLabel(d: "SELLER" | "SYSTEM" | "ADMIN"): string {
  return d === "SELLER" ? "판매자" : d === "SYSTEM" ? "자동 승인" : "관리자";
}

/** 목록 카드의 환불 요약: 완료면 금액, 끝났지만 환불이 없는 상태면 "환불 없음", 진행 중이면 확정 전 안내. */
export function refundSummaryText(r: { status: Status; refundAmount: number }): string {
  if (r.status === "COMPLETED") return formatKrw(r.refundAmount);
  if (r.status === "REJECTED" || r.status === "WITHDRAWN" || r.status === "CANCELED_BY_ADMIN") return "환불 없음";
  return "수거 확인 뒤 확정";
}

/** 서버가 환불 결과를 확정하는 중인가(상세를 3초 간격으로 다시 읽는다. 간격·상한은 features/orders/polling.ts). */
export function needsReturnPolling(status: string): boolean {
  return status === "REFUNDING";
}
