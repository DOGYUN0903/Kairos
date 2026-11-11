import { describe, expect, it } from "vitest";
import { parseSellerFilter, sellerActions } from "./actions";

describe("sellerActions", () => {
  it("승인_판매자만_정지할_수_있다", () => {
    expect(sellerActions("APPROVED")).toEqual({ suspend: true, unsuspend: false });
  });
  it("정지_판매자만_해제할_수_있다", () => {
    expect(sellerActions("SUSPENDED")).toEqual({ suspend: false, unsuspend: true });
  });
  it("심사_중과_반려는_작업이_없다", () => {
    expect(sellerActions("PENDING")).toEqual({ suspend: false, unsuspend: false });
    expect(sellerActions("REJECTED")).toEqual({ suspend: false, unsuspend: false });
  });
});

describe("parseSellerFilter", () => {
  it("모르는_값은_전체로_돌린다", () => {
    expect(parseSellerFilter(null)).toBe("ALL");
    expect(parseSellerFilter("x")).toBe("ALL");
    expect(parseSellerFilter("SUSPENDED")).toBe("SUSPENDED");
  });
});
