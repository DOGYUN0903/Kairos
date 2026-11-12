import { ApiError } from "@/api/client";

export const IDEMPOTENCY_HEADER = "Idempotency-Key";

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

/**
 * 서버가 답한 실패인가(4xx). 응답을 받지 못한 경우(네트워크 오류 status 0, 5xx, ApiError 가 아닌 오류)는 false.
 * IDEMPOTENCY_IN_PROGRESS(409)도 답이 아니다: 같은 키의 첫 요청이 아직 처리 중이라는 뜻이지 저장된 결과가 아니므로
 * 키를 버리면 중복 주문·결제가 생긴다.
 * 답한 실패는 같은 키를 다시 쓰면 안 되고, 답이 없는 실패는 같은 키로 재시도해야 한다.
 */
export function isAnswered(e: unknown): boolean {
  if (e instanceof ApiError && e.code === "IDEMPOTENCY_IN_PROGRESS") return false;
  return e instanceof ApiError && e.status >= 400 && e.status < 500;
}
