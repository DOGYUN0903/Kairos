import { describe, expect, it } from "vitest";
import { canPay, shouldRedirectToCart, unavailableDestination } from "@/features/checkout/checkout-guard";

const ready = { hasQuote: true, fetching: false, placeholder: false, errored: false, orderCreated: false, submitting: false };

describe("canPay", () => {
  it("견적이_확정되면_결제할_수_있다", () => {
    expect(canPay(ready)).toBe(true);
  });
  it("주문이_만들어진_뒤에는_다시_결제할_수_없다", () => {
    expect(canPay({ ...ready, orderCreated: true })).toBe(false);
  });
  it("견적이_재조회_중이면_제출하지_않는다", () => {
    expect(canPay({ ...ready, fetching: true })).toBe(false);
    expect(canPay({ ...ready, placeholder: true })).toBe(false);
  });
  it("견적이_없거나_오류이거나_제출_중이면_결제할_수_없다", () => {
    expect(canPay({ ...ready, hasQuote: false })).toBe(false);
    expect(canPay({ ...ready, errored: true })).toBe(false);
    expect(canPay({ ...ready, submitting: true })).toBe(false);
  });
});

describe("shouldRedirectToCart", () => {
  it("남은_항목이_없으면_장바구니로_보낸다", () => {
    expect(shouldRedirectToCart({ empty: true, orderCreated: false })).toBe(true);
  });
  it("주문이_만들어진_뒤에는_장바구니가_바뀌어도_장바구니로_보내지_않는다", () => {
    expect(shouldRedirectToCart({ empty: true, orderCreated: true })).toBe(false);
  });
});

describe("unavailableDestination", () => {
  it("장바구니_모드는_장바구니로_간다", () => {
    expect(unavailableDestination("cart", 3)).toBe("/cart");
  });
  it("바로_구매는_상품_상세로_가고_모르면_목록으로_간다", () => {
    expect(unavailableDestination("direct", 3)).toBe("/products/3");
    expect(unavailableDestination("direct", undefined)).toBe("/products");
  });
});
