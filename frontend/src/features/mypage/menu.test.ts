import { describe, expect, it } from "vitest";
import { activeMenuHref } from "@/features/mypage/menu";

describe("activeMenuHref", () => {
  it("하위_경로는_상위_메뉴", () => {
    expect(activeMenuHref("/my/orders/12")).toBe("/my/orders");
    expect(activeMenuHref("/my/orders/12/pay")).toBe("/my/orders");
    expect(activeMenuHref("/my/coupons")).toBe("/my/coupons");
  });
  it("메뉴가_아니면_null", () => {
    expect(activeMenuHref("/cart")).toBeNull();
    expect(activeMenuHref("/my/ordersX")).toBeNull();
  });
});
