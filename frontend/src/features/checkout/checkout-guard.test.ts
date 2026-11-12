import { ApiError } from "@/api/client";
import { isAnswered } from "@/api/idempotency";
import { describe, expect, it } from "vitest";
import type { OrderQuote } from "@/features/checkout/api";
import { EMPTY_SELECTION, selectCartCoupon, toBenefitBody } from "@/features/checkout/benefit-selection";
import {
  afterOrderHref,
  canPay,
  orderErrorAction,
  payButtonLabel,
  QuoteNotReadyError,
  shouldRedirectToCart,
  submitSnapshot,
  unavailableDestination,
} from "@/features/checkout/checkout-guard";

const ready = { hasQuote: true, fetching: false, placeholder: false, errored: false, orderCreated: false, pointsSettled: true };

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
  it("포인트_입력이_확정되지_않았으면_결제할_수_없다", () => {
    expect(canPay({ ...ready, pointsSettled: false })).toBe(false);
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
    expect(afterOrderHref(7, "PENDING_PAYMENT", 49000)).toBe("/my/orders/7/pay?auto=1");
    expect(afterOrderHref(7, "PAID", 49000)).toBe("/my/orders/7/complete");
  });
  it("0원_주문은_PAID면_완료_화면_아니면_주문_상세로_간다", () => {
    expect(afterOrderHref(7, "PAID", 0)).toBe("/my/orders/7/complete");
    expect(afterOrderHref(7, "PENDING_PAYMENT", 0)).toBe("/my/orders/7");
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

describe("payButtonLabel", () => {
  it("0원이면_주문하기_아니면_금액_결제하기", () => {
    expect(payButtonLabel(0)).toBe("주문하기");
    expect(payButtonLabel(49000)).toBe("49,000원 결제하기");
  });
});

describe("orderErrorAction", () => {
  it.each(["COUPON_NOT_AVAILABLE", "COUPON_NOT_FOUND", "COUPON_NOT_APPLICABLE", "COUPON_MIN_AMOUNT_NOT_MET"])("%s는_쿠폰을_비운다", (code) => {
    expect(orderErrorAction(code)).toBe("CLEAR_COUPONS");
  });
  it.each(["POINT_INSUFFICIENT", "POINT_EXCEEDS_LIMIT", "POINT_INVALID_AMOUNT"])("%s는_포인트를_비운다", (code) => {
    expect(orderErrorAction(code)).toBe("CLEAR_POINTS");
  });
  it("금액_변경과_모르는_코드는_선택을_유지한다", () => {
    expect(orderErrorAction("ORDER_PRICE_CHANGED")).toBeNull();
    expect(orderErrorAction("STOCK_SOLD_OUT")).toBeNull();
  });
});

describe("submitSnapshot", () => {
  const quote = { totalAmount: 49000 } as OrderQuote;
  const picked = selectCartCoupon(EMPTY_SELECTION, 5);

  it("견적을_만든_선택과_현재_선택이_다르면_null이다", () => {
    expect(submitSnapshot({ quote, quoteSelection: EMPTY_SELECTION, selection: picked, payable: true })).toBeNull();
  });
  it("결제할_수_없으면_null이다", () => {
    expect(submitSnapshot({ quote, quoteSelection: picked, selection: picked, payable: false })).toBeNull();
  });
  it("견적이나_견적의_선택이_없으면_null이다", () => {
    expect(submitSnapshot({ quote: undefined, quoteSelection: picked, selection: picked, payable: true })).toBeNull();
    expect(submitSnapshot({ quote, quoteSelection: null, selection: picked, payable: true })).toBeNull();
  });
  it("같으면_그_견적의_금액과_그_선택을_돌려준다", () => {
    expect(submitSnapshot({ quote, quoteSelection: picked, selection: picked, payable: true })).toEqual({
      expectedTotalAmount: 49000,
      benefit: toBenefitBody(picked),
    });
  });
});
