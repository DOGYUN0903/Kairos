import { ApiError } from "@/api/client";

export const IDEMPOTENCY_HEADER = "Idempotency-Key";

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

/**
 * 서버가 답한 실패인가(4xx). 응답을 받지 못한 경우(네트워크 오류 status 0, 5xx, ApiError 가 아닌 오류)는 false.
 * 답한 실패는 같은 키를 다시 쓰면 안 되고, 답이 없는 실패는 같은 키로 재시도해야 한다.
 */
export function isAnswered(e: unknown): boolean {
  return e instanceof ApiError && e.status >= 400 && e.status < 500;
}
