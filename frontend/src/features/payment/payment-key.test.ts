import { describe, expect, it } from "vitest";
import { mockPaymentKey } from "@/features/payment/payment-key";
import { failUrl, mockSuccessHref, successUrl } from "@/features/payment/payment-urls";

const UUID = "3f0c1a52-8f0e-4a43-9a51-0d6a1f8f6f10";
// 테스트 전용 접두사 표(실제 값은 features/demo 에만 있다).
const prefixes = { reject: "fail-", unknown: "unknown-", lost: "lost-" };

describe("mockPaymentKey", () => {
  it("정상_결제_키는_mock_접두사다", () => {
    expect(mockPaymentKey("approve", UUID)).toBe(`mock-${UUID}`);
    expect(mockPaymentKey("approve", UUID, prefixes)).toBe(`mock-${UUID}`);
  });

  it("결과별_접두사를_붙인다", () => {
    expect(mockPaymentKey("reject", UUID, prefixes)).toBe(`fail-${UUID}`);
    expect(mockPaymentKey("unknown", UUID, prefixes)).toBe(`unknown-${UUID}`);
    expect(mockPaymentKey("lost", UUID, prefixes)).toBe(`lost-${UUID}`);
  });

  it("접두사가_없으면_실패_결과도_정상_키를_만든다", () => {
    expect(mockPaymentKey("reject", UUID)).toBe(`mock-${UUID}`);
    expect(mockPaymentKey("unknown", UUID)).toBe(`mock-${UUID}`);
    expect(mockPaymentKey("lost", UUID)).toBe(`mock-${UUID}`);
  });
});

describe("결제 이동 주소", () => {
  it("성공·실패_주소에_숫자_주문_ID를_붙인다", () => {
    expect(successUrl(42)).toBe("/checkout/success?oid=42");
    expect(failUrl(42)).toBe("/checkout/fail?oid=42");
  });

  it("모의_결제_이동_주소는_토스_성공_리다이렉트와_같은_쿼리를_쓴다", () => {
    const href = mockSuccessHref(42, "K20261112142110Q3ZT8B", `mock-${UUID}`, 48000);
    expect(href).toBe(`/checkout/success?oid=42&orderId=K20261112142110Q3ZT8B&paymentKey=mock-${UUID}&amount=48000`);
  });
});
