import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import { confirmKeyFor } from "@/features/payment/confirm-key-store";
import type { ConfirmParams, ConfirmPaymentResponse, ConfirmResult } from "@/features/payment/confirm-state";

/**
 * 결제 승인. 던지지 않고 결과를 그대로 돌려준다(화면은 toConfirmView 로 정한다).
 * Idempotency-Key 는 paymentKey 마다 하나라서 새로고침·뒤로 가기에도 같은 키가 나간다. 자동 재시도는 없다.
 */
export async function confirmPayment(p: Pick<ConfirmParams, "orderNo" | "paymentKey" | "amount">): Promise<ConfirmResult> {
  try {
    const data = unwrap<ConfirmPaymentResponse>(
      await api.POST("/api/v1/payments/confirm", {
        params: { header: { [IDEMPOTENCY_HEADER]: confirmKeyFor(p.paymentKey) } },
        body: { orderNo: p.orderNo, paymentKey: p.paymentKey, amount: p.amount },
      }),
    );
    return { ok: true, data };
  } catch (error) {
    return { ok: false, error };
  }
}
