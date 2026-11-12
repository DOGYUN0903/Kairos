import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { isAnswered } from "@/api/idempotency";

export type ReceiveResult = { ok: true; status: string; refundAmount: number } | { ok: false; error: unknown };

export type ReceiveOutcome =
  | { kind: "completed"; refundAmount: number }
  | { kind: "refunding" }
  | { kind: "rejected" }
  | { kind: "unknown" }
  | { kind: "error"; message: string };

/**
 * 수거 확인(환불 실행) 결과를 화면 동작으로 나눈다. 응답을 받지 못한 경우(네트워크·5xx·처리 중 409)는 unknown 이다:
 * 환불이 됐는지 모르므로 성공도 실패도 말하지 않고 자동으로 다시 보내지 않는다.
 */
export function receiveOutcome(result: ReceiveResult): ReceiveOutcome {
  if (result.ok) {
    if (result.status === "COMPLETED") return { kind: "completed", refundAmount: result.refundAmount };
    if (result.status === "REFUNDING") return { kind: "refunding" };
    return { kind: "unknown" };
  }
  const e = result.error;
  if (e instanceof ApiError && e.code === "PAYMENT_CANCEL_FAILED") return { kind: "rejected" };
  if (!isAnswered(e)) return { kind: "unknown" };
  return { kind: "error", message: resolveError(e).message };
}
