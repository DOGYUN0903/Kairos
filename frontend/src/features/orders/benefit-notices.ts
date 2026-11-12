import { formatPoints } from "@/lib/money";

/** 항목 취소 창에 더하는 혜택 안내. 포인트 금액은 서버 값(pointsAllocated) 그대로다. */
export function benefitNotices(s: { pointsAllocated: number; hasProductCoupon: boolean; hasCartCoupon: boolean }): string[] {
  const out: string[] = [];
  if (s.pointsAllocated > 0) out.push(`사용한 포인트 ${formatPoints(s.pointsAllocated)}가 함께 돌아옵니다`);
  if (s.hasProductCoupon) out.push("사용한 상품 쿠폰은 유효기간이 남아 있으면 돌아옵니다");
  if (s.hasCartCoupon) out.push("장바구니 쿠폰은 주문 전체를 취소해야 돌아옵니다");
  return out;
}
