import { describe, expect, it } from "vitest";
import { canProcessReturns } from "./actions";

describe("canProcessReturns", () => {
  it("승인된_판매자만_처리할_수_있다", () => {
    expect(canProcessReturns("APPROVED")).toBe(true);
    for (const s of ["SUSPENDED", "PENDING", "REJECTED", "loading", "none"]) expect(canProcessReturns(s)).toBe(false);
  });
});
