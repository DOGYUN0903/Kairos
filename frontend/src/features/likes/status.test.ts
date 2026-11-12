import { describe, expect, it } from "vitest";
import { isOnSale } from "@/features/likes/status";

describe("isOnSale", () => {
  it("ON_SALE만_판매_중이다", () => {
    expect(isOnSale("ON_SALE")).toBe(true);
  });
  it.each(["PAUSED", "ENDED", "PENDING_REVIEW", "REJECTED", "UNKNOWN", ""])("%s는_판매_중이_아니다", (s) => {
    expect(isOnSale(s)).toBe(false);
  });
});
