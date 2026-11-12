import { describe, expect, it } from "vitest";
import { adjustmentFormSchema, adjustmentState, canDeleteAdjustment, toAdjustmentRequest } from "@/features/admin/settlements/adjustment-schema";

const valid = { kind: "SUBTRACT", amount: "10,000", reason: "배송 중 파손 보상 합의", refSellerOrderId: "" };
const parse = (over: Partial<typeof valid> = {}) => adjustmentFormSchema.safeParse({ ...valid, ...over });

describe("adjustmentFormSchema", () => {
  it("차감_10000은_요청_금액이_음수", () => {
    const r = parse();
    expect(r.success).toBe(true);
    if (r.success) expect(toAdjustmentRequest(r.data).amount).toBe(-10000);
  });
  it("가산은_양수", () => {
    const r = parse({ kind: "ADD" });
    if (!r.success) throw new Error("실패");
    expect(toAdjustmentRequest(r.data).amount).toBe(10000);
  });
  it.each(["0", "-1", "1.5", "1e3", "", "abc"])("금액_%s는_거절", (amount) => expect(parse({ amount }).success).toBe(false));
  it("사유가_공백만이면_거절", () => expect(parse({ reason: "   " }).success).toBe(false));
  it("사유_500자는_통과_501자는_거절", () => {
    expect(parse({ reason: "가".repeat(500) }).success).toBe(true);
    expect(parse({ reason: "가".repeat(501) }).success).toBe(false);
  });
  it("사유는_앞뒤_공백을_자른다", () => {
    const r = parse({ reason: "  사유  " });
    if (!r.success) throw new Error("실패");
    expect(toAdjustmentRequest(r.data).reason).toBe("사유");
  });
  it("근거_주문을_비우면_요청에_없다", () => {
    const r = parse();
    if (!r.success) throw new Error("실패");
    expect("refSellerOrderId" in toAdjustmentRequest(r.data)).toBe(false);
  });
  it("근거_주문은_양의_정수만", () => {
    expect(parse({ refSellerOrderId: "4817" }).success).toBe(true);
    for (const v of ["0", "-3", "1.5", "x"]) expect(parse({ refSellerOrderId: v }).success).toBe(false);
    const r = parse({ refSellerOrderId: "4817" });
    if (!r.success) throw new Error("실패");
    expect(toAdjustmentRequest(r.data).refSellerOrderId).toBe(4817);
  });
  it("구분은_ADD_SUBTRACT만", () => expect(parse({ kind: "X" }).success).toBe(false));
});

describe("조정 상태와 삭제 가능 여부", () => {
  it("정산에_붙지_않았으면_PENDING", () => expect(adjustmentState({ type: "MANUAL" })).toBe("PENDING"));
  it("null도_PENDING", () => expect(adjustmentState({ type: "MANUAL", settlementId: null })).toBe("PENDING"));
  it("붙었으면_APPLIED", () => expect(adjustmentState({ type: "MANUAL", settlementId: 5 })).toBe("APPLIED"));
  it("MANUAL_미부착만_삭제_가능", () => {
    expect(canDeleteAdjustment({ type: "MANUAL", settlementId: null })).toBe(true);
    expect(canDeleteAdjustment({ type: "MANUAL", settlementId: 5 })).toBe(false);
    expect(canDeleteAdjustment({ type: "CARRY_OVER", settlementId: null })).toBe(false);
  });
});
