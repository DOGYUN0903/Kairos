import { describe, expect, it } from "vitest";
import { canCancelItem, canConfirmPurchase, canPay, orderHeader, refundLines } from "@/features/orders/actions";
import type { OrderDetail, OrderItemResult, SellerOrderResult } from "@/features/orders/api";
import type { CancelPreview } from "@/features/orders/api";

const item = (status: OrderItemResult["status"] = "ORDERED") => ({ status }) as OrderItemResult;
const seller = (status: SellerOrderResult["status"] = "PAID", hasOpenReturn = false) => ({ status, hasOpenReturn }) as SellerOrderResult;
const order = (over: Partial<OrderDetail> = {}) =>
  ({ status: "PAID", expiresAt: "2999-01-01T00:00:00", payment: { status: "APPROVED" }, ...over }) as OrderDetail;

const NOW = Date.parse("2026-11-12T12:00:00+09:00");

describe("canCancelItem", () => {
  it("주문 결제 완료, 판매자 주문 배송 준비, 항목 주문 완료면 취소할 수 있다", () => {
    expect(canCancelItem(order(), seller("PAID"), item("ORDERED"))).toBe(true);
  });
  it("배송이 시작된 판매자 주문에서는 취소할 수 없다", () => {
    expect(canCancelItem(order(), seller("SHIPPING"), item("ORDERED"))).toBe(false);
  });
  it("취소 확인 중인 항목은 취소할 수 없다", () => {
    expect(canCancelItem(order(), seller("PAID"), item("CANCEL_REQUESTED"))).toBe(false);
  });
  it("결제 대기 주문에서는 취소할 수 없다", () => {
    expect(canCancelItem(order({ status: "PENDING_PAYMENT" }), seller("PAID"), item("ORDERED"))).toBe(false);
  });
  it("반품 요청 항목은 취소할 수 없다", () => {
    expect(canCancelItem(order(), seller("PAID"), item("RETURN_REQUESTED"))).toBe(false);
  });
});

describe("canConfirmPurchase", () => {
  it("배송 완료이고 열린 반품이 없으면 확정할 수 있다", () => {
    expect(canConfirmPurchase(seller("DELIVERED", false))).toBe(true);
  });
  it("열린 반품이 있으면 확정할 수 없다", () => {
    expect(canConfirmPurchase(seller("DELIVERED", true))).toBe(false);
  });
  it("배송 완료가 아니면 확정할 수 없다", () => {
    expect(canConfirmPurchase(seller("SHIPPING"))).toBe(false);
    expect(canConfirmPurchase(seller("CONFIRMED"))).toBe(false);
  });
});

describe("canPay", () => {
  const pending = (over: Partial<OrderDetail> = {}) => order({ status: "PENDING_PAYMENT", payment: { status: "READY" } as OrderDetail["payment"], ...over });
  it("결제 대기이고 기한이 남았으면 결제할 수 있다", () => {
    expect(canPay(pending(), NOW)).toBe(true);
  });
  it("만료 시각이 지나면 결제할 수 없다", () => {
    expect(canPay(pending({ expiresAt: "2026-11-12T11:59:00" }), NOW)).toBe(false);
  });
  it("결제 확인 중이면 결제할 수 없다", () => {
    expect(canPay(pending({ payment: { status: "IN_PROGRESS" } as OrderDetail["payment"] }), NOW)).toBe(false);
    expect(canPay(pending({ payment: { status: "UNKNOWN" } as OrderDetail["payment"] }), NOW)).toBe(false);
  });
  it("결제 대기가 아니면 결제할 수 없다", () => {
    expect(canPay(order(), NOW)).toBe(false);
  });
});

describe("orderHeader", () => {
  const pending = (over: Partial<OrderDetail> = {}) => order({ status: "PENDING_PAYMENT", payment: { status: "READY" } as OrderDetail["payment"], ...over });
  it("결제 대기", () => {
    expect(orderHeader(pending(), NOW)).toBe("pending");
  });
  it("결제 대기는 기한이 지나도 서버가 취소할 때까지 결제 대기 헤더다", () => {
    expect(orderHeader(pending({ expiresAt: "2026-11-12T11:00:00" }), NOW)).toBe("pending");
  });
  it("결제가 확인 중이면 확인 중", () => {
    expect(orderHeader(pending({ payment: { status: "UNKNOWN" } as OrderDetail["payment"] }), NOW)).toBe("checking");
    expect(orderHeader(order({ payment: { status: "IN_PROGRESS" } as OrderDetail["payment"] }), NOW)).toBe("checking");
  });
  it("결제 완료", () => {
    expect(orderHeader(order(), NOW)).toBe("paid");
  });
  it("구매 확정", () => {
    expect(orderHeader(order({ status: "COMPLETED" }), NOW)).toBe("completed");
  });
  it("취소", () => {
    expect(orderHeader(order({ status: "CANCELED" }), NOW)).toBe("canceled");
  });
});

describe("refundLines", () => {
  const preview = (over: Partial<CancelPreview> = {}): CancelPreview => ({
    orderItemId: 1,
    paidAmount: 30000,
    shippingFeeRefund: 0,
    shippingFeeDeducted: 0,
    shippingFeeReturned: 0,
    refundAmount: 30000,
    pointsRefund: 0,
    ...over,
  });
  it("27,000원 예시는 항목 금액과 배송비 차감 줄이다", () => {
    expect(refundLines(preview({ shippingFeeDeducted: 3000, refundAmount: 27000 }))).toEqual([
      { label: "항목 금액", amount: 30000, negative: false },
      { label: "배송비 차감", amount: 3000, negative: true },
    ]);
  });
  it("0인 줄은 없고 항목 금액만 남는다", () => {
    expect(refundLines(preview())).toEqual([{ label: "항목 금액", amount: 30000, negative: false }]);
  });
  it("배송비 환불과 차감 배송비 반환도 0이 아닐 때만 나온다", () => {
    expect(refundLines(preview({ shippingFeeRefund: 3000, shippingFeeReturned: 1000, refundAmount: 34000 }))).toEqual([
      { label: "항목 금액", amount: 30000, negative: false },
      { label: "배송비 환불", amount: 3000, negative: false },
      { label: "차감 배송비 반환", amount: 1000, negative: false },
    ]);
  });
});
