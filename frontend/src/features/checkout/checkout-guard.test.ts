import { ApiError } from "@/api/client";
import { isAnswered } from "@/api/idempotency";
import { describe, expect, it } from "vitest";
import { afterOrderHref, canPay, QuoteNotReadyError, shouldRedirectToCart, unavailableDestination } from "@/features/checkout/checkout-guard";

const ready = { hasQuote: true, fetching: false, placeholder: false, errored: false, orderCreated: false };

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
  it("견적이_없거나_오류이면_결제할_수_없다", () => {
    expect(canPay({ ...ready, hasQuote: false })).toBe(false);
    expect(canPay({ ...ready, errored: true })).toBe(false);
  });
});

describe("QuoteNotReadyError", () => {
  it("견적_미확정_오류는_서버가_답한_실패가_아니라서_멱등_키를_유지한다", () => {
    const e = new QuoteNotReadyError();
    expect(e).toBeInstanceOf(Error);
    expect(e).not.toBeInstanceOf(ApiError);
    expect(isAnswered(e)).toBe(false);
  });
});

describe("afterOrderHref", () => {
  it("결제_대기_주문은_결제_페이지로_이미_결제된_주문은_완료_화면으로_간다", () => {
    expect(afterOrderHref(7, "PENDING_PAYMENT")).toBe("/my/orders/7/pay?auto=1");
    expect(afterOrderHref(7, "PAID")).toBe("/my/orders/7/complete");
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
