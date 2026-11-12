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

export function feeBearerNotice(b: "BUYER" | "SELLER"): string {
  return b === "BUYER" ? "반품 배송비는 구매자 부담입니다" : "판매자 부담이라 차감이 없습니다";
}

export function decidedByLabel(d: "SELLER" | "SYSTEM" | "ADMIN"): string {
  return d === "SELLER" ? "판매자" : d === "SYSTEM" ? "자동 승인" : "관리자";
}
