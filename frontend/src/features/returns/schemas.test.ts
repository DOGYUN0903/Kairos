import { describe, expect, it } from "vitest";
import { returnRequestSchema, toReturnBody } from "@/features/returns/schemas";

describe("returnRequestSchema", () => {
  it("사유가_없으면_선택_안내_메시지를_돌려준다", () => {
    const r = returnRequestSchema.safeParse({ detail: "" });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.issues[0]?.message).toBe("반품 사유를 선택해 주세요");
  });

  it("상세_내용은_500자까지_허용하고_501자부터_거절한다", () => {
    expect(returnRequestSchema.safeParse({ reason: "DEFECTIVE", detail: "가".repeat(500) }).success).toBe(true);
    expect(returnRequestSchema.safeParse({ reason: "DEFECTIVE", detail: "가".repeat(501) }).success).toBe(false);
  });

  it("상세_내용은_비워도_된다", () => {
    expect(returnRequestSchema.safeParse({ reason: "CHANGE_OF_MIND", detail: "" }).success).toBe(true);
  });
});

describe("toReturnBody", () => {
  it("빈_상세_내용은_요청_본문에_넣지_않는다", () => {
    expect(toReturnBody({ reason: "WRONG_DELIVERY", detail: "" })).toEqual({ reason: "WRONG_DELIVERY" });
    expect(toReturnBody({ reason: "WRONG_DELIVERY", detail: "   " })).toEqual({ reason: "WRONG_DELIVERY" });
  });

  it("상세_내용이_있으면_앞뒤_공백을_뺀_값을_보낸다", () => {
    expect(toReturnBody({ reason: "DEFECTIVE", detail: " 단추가 떨어졌어요 " })).toEqual({ reason: "DEFECTIVE", detail: "단추가 떨어졌어요" });
  });
});
