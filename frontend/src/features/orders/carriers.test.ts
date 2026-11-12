import { describe, expect, it } from "vitest";
import { carrierName, CARRIERS } from "@/features/orders/carriers";

describe("carriers", () => {
  it("코드로_택배사_이름을_찾는다", () => {
    expect(carrierName("04")).toBe("CJ대한통운");
    expect(CARRIERS).toHaveLength(5);
  });
  it("모르는_코드는_코드_그대로", () => {
    expect(carrierName("99")).toBe("99");
  });
  it("null과_undefined는_빈_문자열", () => {
    expect(carrierName(null)).toBe("");
    expect(carrierName(undefined)).toBe("");
  });
});
