import type { components } from "@/api/schema";
import { hasPassedKst } from "@/lib/datetime";

export type CouponPolicyResult = components["schemas"]["CouponPolicyResult"];
export type CreateCouponPolicyRequest = components["schemas"]["CreateCouponPolicyRequest"];

export type PolicyDisplayStatus = "ACTIVE" | "ENDED" | "PERIOD_OVER";

/** 서버 상태가 ACTIVE 여도 발급 기간(validUntil, KST)이 지났으면 "기간 종료"로 보인다. ENDED 는 기간과 무관하다. */
export function policyDisplayStatus(p: { status: "ACTIVE" | "ENDED"; validUntil: string }, nowMs: number): PolicyDisplayStatus {
  if (p.status === "ENDED") return "ENDED";
  return hasPassedKst(p.validUntil, nowMs) ? "PERIOD_OVER" : "ACTIVE";
}

const count = new Intl.NumberFormat("ko-KR");

/** "12 / 100", 무제한이면 "12 / 제한 없음". */
export function quantityLabel(issued: number, total: number | null | undefined): string {
  return `${count.format(issued)} / ${total == null ? "제한 없음" : count.format(total)}`;
}

export function targetLabel(p: Pick<CouponPolicyResult, "targetType" | "targetProductId" | "targetProductName">): string {
  switch (p.targetType) {
    case "ALL_PRODUCTS":
      return "전 상품";
    case "SELLER":
      return "내 상품 전체";
    case "NONE":
      return "주문 전체";
    case "PRODUCT":
      return p.targetProductName ?? `상품 #${p.targetProductId}`;
  }
}
