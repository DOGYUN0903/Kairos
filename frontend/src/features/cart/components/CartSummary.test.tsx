import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import type { OrderQuote } from "@/features/checkout/api";
import { CartSummary } from "@/features/cart/components/CartSummary";

const quote = {
  itemsAmount: 40000,
  buyerShippingFee: 3000,
  totalAmount: 43000,
  freeShippingThreshold: 50000,
  sellerQuotes: [{ sellerId: 1, sellerName: "판매자", shippingFee: 3000, shippingFeePayer: "BUYER" }],
} as unknown as OrderQuote;

const failed = new ApiError(503, "INTERNAL_ERROR", "서버 오류", null);

describe("CartSummary", () => {
  it("실패_뒤_다시_시도하는_동안에는_다시_계산_중이다", () => {
    const { container } = render(<CartSummary selectedIds={[1]} quote={undefined} stale error={failed} onRetry={() => {}} />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText("—")).not.toBeInTheDocument();
  });

  it("견적이_오면_서버_금액을_보여주고_주문할_수_있다", () => {
    const { container } = render(<CartSummary selectedIds={[1]} quote={quote} stale={false} error={null} onRetry={() => {}} />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "false");
    expect(screen.getByText("43,000원")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주문하기 (1)" })).toBeInTheDocument();
  });

  it("견적을_기다리는_동안에는_계산_중으로_표시한다", () => {
    const { container } = render(<CartSummary selectedIds={[1]} quote={undefined} stale error={null} onRetry={() => {}} />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "true");
    expect(screen.getAllByText("계산 중").length).toBeGreaterThan(0);
  });

  it.each([
    ["견적이 한 번도 오지 않음", undefined],
    ["이전 견적이 남아 있음", quote],
  ])("견적이_실패하면_계산_중을_멈추고_금액_대신_중립_표시와_재시도만_보인다 (%s)", async (_name, previous) => {
    const onRetry = vi.fn();
    const { container } = render(<CartSummary selectedIds={[1]} quote={previous} stale={false} error={failed} onRetry={onRetry} />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "false");
    expect(screen.queryByText("계산 중")).not.toBeInTheDocument();
    // 0원이나 이전 선택의 금액을 지금 금액처럼 보여 주지 않는다.
    expect(container.textContent).not.toMatch(/\d원/);
    expect(screen.getAllByText("—")).toHaveLength(3);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /주문하기/ })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
