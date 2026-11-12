import { describe, expect, it } from "vitest";
import type { OrderDetail } from "@/features/orders/api";
import { canPay, isPaid, orderNameOf } from "@/features/payment/pay-state";

const NOW = Date.parse("2026-11-12T14:21:00+09:00");

function order(over: Partial<{ status: OrderDetail["status"]; expiresAt: string; payment: string | null; names: string[] }> = {}): OrderDetail {
  const payment = over.payment === undefined ? "READY" : over.payment;
  return {
    status: over.status ?? "PENDING_PAYMENT",
    totalAmount: 48000,
    expiresAt: over.expiresAt ?? "2026-11-12T14:36:00",
    payment: payment ? { amount: 48000, refundedAmount: 0, status: payment } : null,
    sellerOrders: [
      { items: (over.names ?? ["티셔츠"]).slice(0, 1).map((productName) => ({ productName })) },
      { items: (over.names ?? ["티셔츠"]).slice(1).map((productName) => ({ productName })) },
    ],
  } as unknown as OrderDetail;
}

describe("canPay", () => {
  it("결제_대기이고_만료_전이며_결제가_진행_중이_아니면_결제할_수_있다", () => {
    expect(canPay(order(), NOW)).toBe(true);
    expect(canPay(order({ payment: null }), NOW)).toBe(true);
  });

  it("결제_금액이_0원이면_결제창을_열지_않는다", () => {
    expect(canPay({ ...order(), totalAmount: 0 } as OrderDetail, NOW)).toBe(false);
  });

  it("결제_대기가_아니면_결제할_수_없다", () => {
    expect(canPay(order({ status: "PAID", payment: "APPROVED" }), NOW)).toBe(false);
    expect(canPay(order({ status: "CANCELED" }), NOW)).toBe(false);
    expect(canPay(order({ status: "COMPLETED", payment: "APPROVED" }), NOW)).toBe(false);
  });

  it("결제_기한이_지났으면_결제할_수_없다", () => {
    expect(canPay(order({ expiresAt: "2026-11-12T14:21:00" }), NOW)).toBe(false);
    expect(canPay(order({ expiresAt: "2026-11-12T14:20:59.999999" }), NOW)).toBe(false);
    expect(canPay(order({ expiresAt: "잘못된 값" }), NOW)).toBe(false);
  });

  it("결제가_진행_중이거나_결과를_모르면_결제할_수_없다", () => {
    expect(canPay(order({ payment: "IN_PROGRESS" }), NOW)).toBe(false);
    expect(canPay(order({ payment: "UNKNOWN" }), NOW)).toBe(false);
  });
});

describe("isPaid", () => {
  it("결제_완료와_구매_확정만_완료다", () => {
    expect(isPaid(order({ status: "PAID" }))).toBe(true);
    expect(isPaid(order({ status: "COMPLETED" }))).toBe(true);
    expect(isPaid(order({ status: "PENDING_PAYMENT" }))).toBe(false);
    expect(isPaid(order({ status: "CANCELED" }))).toBe(false);
  });
});

describe("orderNameOf", () => {
  it("상품이_하나면_상품명만_쓴다", () => {
    expect(orderNameOf(order({ names: ["티셔츠"] }))).toBe("티셔츠");
  });

  it("여러_개면_첫_상품명_외_n건이다", () => {
    expect(orderNameOf(order({ names: ["티셔츠", "양말", "모자"] }))).toBe("티셔츠 외 2건");
  });
});
