import { formatPoints } from "@/lib/money";

const LABELS: Record<string, string> = {
  EARN_PURCHASE: "구매 확정 적립",
  EARN_REVIEW: "후기 작성 적립",
  USE: "주문 사용",
  REFUND: "주문 취소 환급",
  EXPIRE: "기간 만료",
};

/** 유형 → 문구. 모르는 값은 그대로 보여준다. */
export function transactionLabel(type: string): string {
  return LABELS[type] ?? type;
}

/** `ORDER` 참조만 주문 ID 라서 링크를 만든다. 다른 refType 은 주문 ID 를 알 수 없다. */
export function transactionOrderHref(t: { refType: string; refId: number }): string | null {
  return t.refType === "ORDER" ? `/my/orders/${t.refId}` : null;
}

/** 서버의 부호 있는 변동량 표기: 214 → "+214P", -10000 → "−10,000P"(U+2212), 0 → "0P". */
export function signedPoints(amount: number): string {
  const text = formatPoints(amount);
  if (amount > 0) return `+${text}`;
  return text.replace("-", "−");
}
