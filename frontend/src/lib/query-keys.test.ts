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
});
