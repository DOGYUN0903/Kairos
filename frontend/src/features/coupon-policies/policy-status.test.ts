import { describe, expect, it } from "vitest";
import { policyDisplayStatus, quantityLabel, targetLabel } from "@/features/coupon-policies/policy-status";

const NOW = Date.parse("2026-12-01T00:00:00+09:00");

describe("policyDisplayStatus", () => {
  it("진행 중이지만 기간이 지났으면 기간 종료", () => {
    expect(policyDisplayStatus({ status: "ACTIVE", validUntil: "2026-11-30T23:59:00" }, NOW)).toBe("PERIOD_OVER");
  });
  it("진행 중이고 기간이 남았으면 진행 중", () => {
    expect(policyDisplayStatus({ status: "ACTIVE", validUntil: "2026-12-31T23:59:00" }, NOW)).toBe("ACTIVE");
  });
  it("종료는 기간과 무관하게 종료", () => {
    expect(policyDisplayStatus({ status: "ENDED", validUntil: "2026-11-30T23:59:00" }, NOW)).toBe("ENDED");
    expect(policyDisplayStatus({ status: "ENDED", validUntil: "2026-12-31T23:59:00" }, NOW)).toBe("ENDED");
  });
});

describe("quantityLabel", () => {
  it("발급 수 / 총 수량", () => {
    expect(quantityLabel(12, 100)).toBe("12 / 100");
  });
  it("총 수량이 없으면 제한 없음", () => {
    expect(quantityLabel(12, null)).toBe("12 / 제한 없음");
    expect(quantityLabel(12, undefined)).toBe("12 / 제한 없음");
  });
});

describe("targetLabel", () => {
  it("대상 유형별 문구", () => {
    expect(targetLabel({ targetType: "ALL_PRODUCTS" })).toBe("전 상품");
    expect(targetLabel({ targetType: "SELLER" })).toBe("내 상품 전체");
    expect(targetLabel({ targetType: "NONE" })).toBe("주문 전체");
    expect(targetLabel({ targetType: "PRODUCT", targetProductId: 123, targetProductName: "볼캡" })).toBe("볼캡");
  });
  it("상품 이름이 없으면 상품 번호", () => {
    expect(targetLabel({ targetType: "PRODUCT", targetProductId: 123 })).toBe("상품 #123");
    expect(targetLabel({ targetType: "PRODUCT", targetProductId: 123, targetProductName: null })).toBe("상품 #123");
  });
});
