import { describe, expect, it } from "vitest";
import { conditionLabel, discountLabel } from "@/features/coupons/discount-text";

describe("discountLabel", () => {
  it("정액", () => {
    expect(discountLabel({ discountType: "FIXED", discountValue: 3000 })).toBe("3,000원 할인");
  });
  it("정률_최대_있음", () => {
    expect(discountLabel({ discountType: "RATE", discountValue: 1000, maxDiscount: 5000 })).toBe("10% 할인 (최대 5,000원)");
  });
  it("정률_최대_없음", () => {
    expect(discountLabel({ discountType: "RATE", discountValue: 1000 })).toBe("10% 할인");
    expect(discountLabel({ discountType: "RATE", discountValue: 1000, maxDiscount: null })).toBe("10% 할인");
  });
});

describe("conditionLabel", () => {
  it("최소_주문_금액", () => {
    expect(conditionLabel({ discountType: "FIXED", discountValue: 1, minOrderAmount: 30000 })).toBe("30,000원 이상 구매 시");
  });
  it("없음_또는_0이면_null", () => {
    expect(conditionLabel({ discountType: "FIXED", discountValue: 1 })).toBeNull();
    expect(conditionLabel({ discountType: "FIXED", discountValue: 1, minOrderAmount: 0 })).toBeNull();
  });
});
