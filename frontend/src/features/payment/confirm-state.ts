import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { isAnswered } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { recallOrderId } from "@/features/payment/order-ref-store";

export type ConfirmPaymentResponse = components["schemas"]["ConfirmPaymentResponse"];

export type ConfirmParams = { orderNo: string; paymentKey: string; amount: number; orderId: number | null };

export type ConfirmView =
  | { kind: "approved"; orderId: number }
  | { kind: "pending" }
  | { kind: "rejected" }
  | { kind: "error"; message: string };

export type ConfirmResult = { ok: true; data: ConfirmPaymentResponse } | { ok: false; error: unknown };

const DIGITS = /^\d{1,15}$/;
const ORDER_ID = /^[1-9]\d{0,15}$/;

/**
 * 성공 주소의 쿼리(토스 형식: orderId=주문번호, paymentKey, amount + 우리 oid=숫자 주문 ID)를 읽는다.
 * 승인에 필요한 값이 빠졌거나 amount 가 정수가 아니면 null. oid 가 없거나 잘못됐으면 결제창을 열 때 적어 둔 값을 쓴다.
 */
export function parseConfirmParams(sp: { get(name: string): string | null }): ConfirmParams | null {
  const orderNo = sp.get("orderId");
  const paymentKey = sp.get("paymentKey");
  const amount = sp.get("amount");
  if (!orderNo || !paymentKey || amount === null || !DIGITS.test(amount)) return null;
  const oid = sp.get("oid");
  const orderId = oid !== null && ORDER_ID.test(oid) ? Number(oid) : recallOrderId(orderNo);
  return { orderNo, paymentKey, amount: Number(amount), orderId };
}

/**
 * 승인 응답 → 화면. 결과를 모르는 경우는 전부 "확인 중"이다(완료도 실패도 단정하지 않는다).
 * - 200 APPROVED 만 승인. 200 의 그 밖의 상태(UNKNOWN 등)는 확인 중
 * - 처리 중(409 PAYMENT_IN_PROGRESS·IDEMPOTENCY_IN_PROGRESS), 응답 없음(네트워크 오류·5xx·ApiError 가 아닌 오류)은 확인 중
 * - 402 PAYMENT_REJECTED 만 거절(서버가 주문을 취소하고 재고를 되돌렸다)
 * - 그 밖에 서버가 답한 오류는 서버 메시지
 */
export function toConfirmView(result: ConfirmResult): ConfirmView {
  if (result.ok) {
    return result.data.paymentStatus === "APPROVED" ? { kind: "approved", orderId: result.data.orderId } : { kind: "pending" };
  }
  const e = result.error;
  if (!isAnswered(e)) return { kind: "pending" };
  if (e instanceof ApiError && e.code === "PAYMENT_IN_PROGRESS") return { kind: "pending" };
  if (e instanceof ApiError && e.status === 402 && e.code === "PAYMENT_REJECTED") return { kind: "rejected" };
  return { kind: "error", message: resolveError(e).message || "결제를 처리하지 못했습니다." };
}
