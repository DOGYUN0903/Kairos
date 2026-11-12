import { describe, expect, it } from "vitest";
import { checkoutHref } from "@/features/checkout/checkout-params";

describe("주문서 주소", () => {
  it("장바구니_주문은_항목_ID를_쉼표로_잇는다", () => {
    expect(checkoutHref.fromCart([1, 2])).toBe("/checkout?cartItemIds=1,2");
  });
  it("바로_구매는_SKU와_수량을_담는다", () => {
    expect(checkoutHref.direct(7, 3)).toBe("/checkout?skuId=7&qty=3");
  });
});
