import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { OrderDetail } from "@/features/orders/api";
import { PaymentSummaryCard } from "@/features/orders/components/PaymentSummaryCard";

const order = (over: Partial<OrderDetail> = {}) =>
  ({
    status: "PAID",
    itemsAmount: 20000,
    buyerShippingFee: 500,
    productCouponDiscount: 0,
    cartCouponDiscount: 0,
    pointsUsed: 0,
    totalAmount: 20500,
    refundedAmount: 0,
    pointsRefunded: 0,
    shippingFeeRecovered: 0,
    remainingAmount: 20500,
    paidAt: "2026-11-12T10:00:00",
    ...over,
  }) as OrderDetail;

describe("PaymentSummaryCard", () => {
  it("결제_전에_취소된_주문에는_남은_결제_금액이_없고_결제_금액은_남는다", () => {
    render(<PaymentSummaryCard order={order({ status: "CANCELED", paidAt: null })} />);
    expect(screen.queryByText("남은 결제 금액")).toBeNull();
    expect(screen.getByText("결제 금액")).toBeTruthy();
  });
  it("결제된_주문에는_남은_결제_금액이_보인다", () => {
    render(<PaymentSummaryCard order={order()} />);
    expect(screen.getByText("남은 결제 금액")).toBeTruthy();
  });
  it("할인·포인트_줄은_0이_아닐_때만_시안_순서로_보인다", () => {
    const { container, rerender } = render(<PaymentSummaryCard order={order()} />);
    for (const label of ["상품 쿠폰 할인", "장바구니 쿠폰 할인", "포인트 사용", "되돌려받은 포인트"]) expect(screen.queryByText(label)).toBeNull();
    rerender(<PaymentSummaryCard order={order({ productCouponDiscount: 2000, cartCouponDiscount: 5000, pointsUsed: 10000, pointsRefunded: 3000 })} />);
    const labels = Array.from(container.querySelectorAll("dt")).map((e) => e.textContent);
    expect(labels.indexOf("상품 쿠폰 할인")).toBeLessThan(labels.indexOf("장바구니 쿠폰 할인"));
    expect(labels.indexOf("장바구니 쿠폰 할인")).toBeLessThan(labels.indexOf("포인트 사용"));
    expect(labels.indexOf("포인트 사용")).toBeLessThan(labels.indexOf("되돌려받은 포인트"));
    expect(screen.getByText("−10,000P")).toBeTruthy();
    expect(screen.getByText("3,000P")).toBeTruthy();
  });
});

describe("PaymentSummaryCard 반품 배송비 차감 안내", () => {
  const withItems = (...items: { returnRequest: { status: string; returnFee: number } | null }[]) =>
    order({ refundedAmount: 14000, sellerOrders: [{ items }] as unknown as OrderDetail["sellerOrders"] });
  const NOTE = "반품 배송비가 차감된 금액입니다";

  it("반품이_완료되고_반품비가_있는_항목이_있으면_보인다", () => {
    render(<PaymentSummaryCard order={withItems({ returnRequest: { status: "COMPLETED", returnFee: 3000 } })} />);
    expect(screen.getByText(NOTE)).toBeTruthy();
  });
  it("판매자_귀책처럼_반품비가_0이면_보이지_않는다", () => {
    render(<PaymentSummaryCard order={withItems({ returnRequest: { status: "COMPLETED", returnFee: 0 } })} />);
    expect(screen.queryByText(NOTE)).toBeNull();
  });
  it("완료되지_않은_반품이나_반품이_없으면_보이지_않는다", () => {
    render(<PaymentSummaryCard order={withItems({ returnRequest: { status: "REFUNDING", returnFee: 3000 } }, { returnRequest: null })} />);
    expect(screen.queryByText(NOTE)).toBeNull();
  });
});
