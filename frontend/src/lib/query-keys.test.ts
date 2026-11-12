import { describe, expect, it } from "vitest";
import { queryKeys } from "@/lib/query-keys";

describe("queryKeys", () => {
  it("quote는_benefit이_없으면_넷째_원소가_없다", () => {
    expect(queryKeys.checkout.quote([1])).toEqual(["checkout", "quote", [1]]);
  });
  it("quote는_benefit이_있으면_접두사_뒤에_붙는다", () => {
    const k = queryKeys.checkout.quote([1], { pointAmount: 1000 });
    expect(k).toEqual(["checkout", "quote", [1], { pointAmount: 1000 }]);
    expect(k.slice(0, 3)).toEqual(queryKeys.checkout.quote([1]));
  });
  it("무효화_접두사가_유지된다", () => {
    expect(queryKeys.reviews.product(3, 0).slice(0, 3)).toEqual([...queryKeys.reviews.productRoot(3)]);
    expect(queryKeys.coupons.policies(3).slice(0, 2)).toEqual([...queryKeys.coupons.policiesRoot]);
    expect(queryKeys.coupons.mine({ a: 1 })[0]).toBe(queryKeys.coupons.mineRoot[0]);
  });
  it("F4_반품·정산_키", () => {
    expect(queryKeys.returns.root).toEqual(["returns"]);
    expect(queryKeys.returns.mine()).toEqual(["returns", "mine", {}]);
    expect(queryKeys.returns.mine({ page: 1 })).toEqual(["returns", "mine", { page: 1 }]);
    expect(queryKeys.returns.detail(3)).toEqual(["returns", "detail", 3]);
    expect(queryKeys.returns.preview(1, 2, "DEFECTIVE")).toEqual(["returns", "preview", 1, 2, "DEFECTIVE"]);
    expect(queryKeys.sellerReturns.lists).toEqual(["sellerReturns", "list"]);
    expect(queryKeys.sellerReturns.list()).toEqual(["sellerReturns", "list", {}]);
    expect(queryKeys.sellerReturns.detail(3)).toEqual(["sellerReturns", "detail", 3]);
    expect(queryKeys.adminReturns.lists).toEqual(["adminReturns", "list"]);
    expect(queryKeys.adminReturns.list({ a: 1 })).toEqual(["adminReturns", "list", { a: 1 }]);
    expect(queryKeys.adminReturns.detail(3)).toEqual(["adminReturns", "detail", 3]);
    expect(queryKeys.sellerSettlements.lists).toEqual(["sellerSettlements", "list"]);
    expect(queryKeys.sellerSettlements.list()).toEqual(["sellerSettlements", "list", {}]);
    expect(queryKeys.sellerSettlements.detail(3)).toEqual(["sellerSettlements", "detail", 3]);
    expect(queryKeys.adminSettlements.lists).toEqual(["adminSettlements", "list"]);
    expect(queryKeys.adminSettlements.list()).toEqual(["adminSettlements", "list", {}]);
    expect(queryKeys.adminSettlements.detail(3)).toEqual(["adminSettlements", "detail", 3]);
    expect(queryKeys.adminAdjustments(7)).toEqual(["adminAdjustments", 7]);
  });
});
