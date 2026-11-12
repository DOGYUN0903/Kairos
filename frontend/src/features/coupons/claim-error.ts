import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";

const REFETCH_MESSAGES: Partial<Record<string, string>> = {
  COUPON_ALREADY_ISSUED: "이미 받은 쿠폰입니다.",
  COUPON_SOLD_OUT: "쿠폰이 모두 소진되었습니다.",
  COUPON_POLICY_ENDED: "종료된 쿠폰입니다.",
};

/**
 * 쿠폰 받기 실패를 화면 동작으로 옮긴다.
 * 이미 받음·소진·종료는 오류가 아니라 안내이고, 서버 상태가 달라졌으니 정책 목록을 다시 읽는다.
 */
export function claimOutcome(e: unknown): { message: string; refetch: boolean } {
  if (e instanceof ApiError) {
    const message = REFETCH_MESSAGES[e.code];
    if (message) return { message, refetch: true };
  }
  return { message: resolveError(e).message, refetch: false };
}
