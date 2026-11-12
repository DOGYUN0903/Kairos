import { z } from "zod";
import type { components } from "@/api/schema";
import { parseWon, signedAmount } from "@/lib/money";

export type CreateSettlementAdjustmentRequest = components["schemas"]["CreateSettlementAdjustmentRequest"];
export const REASON_MAX = 500;

export const adjustmentFormSchema = z.object({
  kind: z.enum(["ADD", "SUBTRACT"], { message: "구분을 선택해 주세요" }),
  amount: z
    .string()
    .transform((s, ctx) => {
      const n = parseWon(s);
      if (n === null || n <= 0) {
        ctx.addIssue({ code: "custom", message: "금액은 1원 이상의 정수로 입력해 주세요" });
        return z.NEVER;
      }
      return n;
    }),
  reason: z
    .string()
    .trim()
    .min(1, "사유를 입력해 주세요")
    .max(REASON_MAX, `사유는 ${REASON_MAX}자 이하로 입력해 주세요`),
  refSellerOrderId: z
    .string()
    .trim()
    .transform((s, ctx) => {
      if (s === "") return undefined;
      const n = /^[1-9]\d{0,14}$/.test(s) ? Number(s) : null;
      if (n === null) {
        ctx.addIssue({ code: "custom", message: "판매자 주문 ID는 양의 정수로 입력해 주세요" });
        return z.NEVER;
      }
      return n;
    }),
});

export type AdjustmentFormInput = z.input<typeof adjustmentFormSchema>;
export type AdjustmentFormValues = z.output<typeof adjustmentFormSchema>;

/** 차감이면 음수로 보낸다. 부호 붙이기 말고 다른 금액 계산은 하지 않는다. */
export function toAdjustmentRequest(v: AdjustmentFormValues): CreateSettlementAdjustmentRequest {
  return {
    amount: signedAmount(v.kind, v.amount),
    reason: v.reason,
    ...(v.refSellerOrderId !== undefined ? { refSellerOrderId: v.refSellerOrderId } : {}),
  };
}

export function adjustmentState(a: { type: "MANUAL" | "CARRY_OVER"; settlementId?: number | null }): "PENDING" | "APPLIED" {
  return a.settlementId == null ? "PENDING" : "APPLIED";
}

/** 미부착 MANUAL 조정만 지울 수 있다. */
export function canDeleteAdjustment(a: { type: "MANUAL" | "CARRY_OVER"; settlementId?: number | null }): boolean {
  return a.type === "MANUAL" && a.settlementId == null;
}
