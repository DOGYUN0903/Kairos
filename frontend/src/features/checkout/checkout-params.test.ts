import { describe, expect, it } from "vitest";
import type { Cart } from "@/features/cart/api";
import { checkoutHref, parseCheckoutParams, resolveCheckoutItems } from "@/features/checkout/checkout-params";

const sp = (q: string) => new URLSearchParams(q);

describe("주문서 주소", () => {
  it("장바구니_주문은_항목_ID를_쉼표로_잇는다", () => {
    expect(checkoutHref.fromCart([1, 2])).toBe("/checkout?cartItemIds=1,2");
  });
  it("바로_구매는_SKU와_수량을_담는다", () => {
    expect(checkoutHref.direct(7, 3)).toBe("/checkout?skuId=7&qty=3");
  });
});

describe("parseCheckoutParams", () => {
  it("cartItemIds를_숫자_배열로_읽고_중복을_없앤다", () => {
    expect(parseCheckoutParams(sp("cartItemIds=3,1,3"))).toEqual({ mode: "cart", cartItemIds: [3, 1] });
  });
  it("skuId와_qty를_읽는다", () => {
    expect(parseCheckoutParams(sp("skuId=5&qty=2"))).toEqual({ mode: "direct", skuId: 5, quantity: 2 });
    expect(parseCheckoutParams(sp("skuId=5&qty=999"))).toEqual({ mode: "direct", skuId: 5, quantity: 999 });
  });
  it.each(["cartItemIds=abc", "cartItemIds=", "cartItemIds=1,,2", "skuId=5", "skuId=5&qty=0", "skuId=5&qty=1000", "skuId=5&qty=1.5", "skuId=x&qty=1", "skuId=5&qty=2&cartItemIds=1", ""])(
    "잘못된_쿼리는_null이다 (%s)",
    (q) => {
      expect(parseCheckoutParams(sp(q))).toBeNull();
    },
  );
});

const item = (cartItemId: number, skuId: number, quantity: number, availability: string) =>
  ({ cartItemId, skuId, quantity, availability }) as unknown as Cart["items"][number];

describe("resolveCheckoutItems", () => {
  it("바로_구매는_그대로_한_건이다", () => {
    expect(resolveCheckoutItems({ mode: "direct", skuId: 5, quantity: 2 }, undefined)).toEqual([{ skuId: 5, quantity: 2 }]);
  });
  it("장바구니_모드는_장바구니를_읽기_전에는_null이다", () => {
    expect(resolveCheckoutItems({ mode: "cart", cartItemIds: [1] }, undefined)).toBeNull();
  });
  it("장바구니에_없거나_구매_불가인_항목은_빠지고_남은_것이_없으면_null이다", () => {
    const cart = { items: [item(1, 10, 2, "AVAILABLE"), item(2, 20, 1, "SOLD_OUT"), item(3, 30, 4, "AVAILABLE")] } as unknown as Cart;
    expect(resolveCheckoutItems({ mode: "cart", cartItemIds: [1, 2, 99] }, cart)).toEqual([{ skuId: 10, quantity: 2 }]);
    expect(resolveCheckoutItems({ mode: "cart", cartItemIds: [2, 99] }, cart)).toBeNull();
  });
});
