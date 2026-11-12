import type { OrderItemReview } from "@/features/reviews/api";
import { formatPoints } from "@/lib/money";

export type ReviewAction =
  | { kind: "none" }
  | { kind: "write"; until: string }
  | { kind: "view"; reviewId: number }
  | { kind: "text"; text: string };

/** 주문 항목의 후기 상태를 화면 동작으로 바꾼다. 필요한 값(기한·reviewId)이 없으면 아무것도 보이지 않는다. */
export function reviewAction(review: OrderItemReview | null | undefined): ReviewAction {
  if (!review) return { kind: "none" };
  switch (review.status) {
    case "WRITABLE":
      return review.writableUntil ? { kind: "write", until: review.writableUntil } : { kind: "none" };
    case "WRITTEN":
      return review.reviewId != null ? { kind: "view", reviewId: review.reviewId } : { kind: "none" };
    case "EXPIRED":
      return { kind: "text", text: "후기 작성 기한이 지났습니다" };
    case "DELETED":
      return { kind: "text", text: "삭제한 후기는 다시 작성할 수 없습니다" };
    default:
      return { kind: "none" };
  }
}

/** 작성 성공 토스트. 포인트는 서버 값 그대로다. */
export function rewardNotice(rewardPoints: number): string {
  return rewardPoints > 0 ? `후기를 등록했습니다. ${formatPoints(rewardPoints)}가 적립되었습니다.` : "후기를 등록했습니다.";
}
