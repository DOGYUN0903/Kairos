import { describe, expect, it } from "vitest";
import { MY_MENU, activeMenuHref } from "@/features/mypage/menu";

describe("activeMenuHref", () => {
  it("하위_경로는_상위_메뉴", () => {
    expect(activeMenuHref("/my/orders/12")).toBe("/my/orders");
    expect(activeMenuHref("/my/orders/12/pay")).toBe("/my/orders");
    expect(activeMenuHref("/my/coupons")).toBe("/my/coupons");
  });
  it("반품_내역은_주문_내역_다음", () => {
    expect(MY_MENU.map((m) => m.href).slice(0, 2)).toEqual(["/my/orders", "/my/returns"]);
    expect(MY_MENU[1]!.label).toBe("반품 내역");
    expect(activeMenuHref("/my/returns/3")).toBe("/my/returns");
  });
  it("메뉴가_아니면_null", () => {
    expect(activeMenuHref("/cart")).toBeNull();
    expect(activeMenuHref("/my/ordersX")).toBeNull();
  });
});
