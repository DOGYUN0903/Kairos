import { describe, expect, it } from "vitest";
import { assertSafeInt, discountRate, formatBp, formatKrw, lineTotal, parseWon } from "@/lib/money";

describe("money", () => {
  it("formatKrw는_27300을_27_300원으로_만든다", () => {
    expect(formatKrw(27300)).toBe("27,300원");
  });
  it("discountRate(39000, 27300)은_30", () => {
    expect(discountRate(39000, 27300)).toBe(30);
  });
  it("discountRate는_버림이다(10000, 6667 → 33)", () => {
    expect(discountRate(10000, 6667)).toBe(33);
  });
  it("정가_0이면_throw", () => {
    expect(() => discountRate(0, 0)).toThrow();
  });
  it("lineTotal(27300, 2000, 3)은_87900", () => {
    expect(lineTotal(27300, 2000, 3)).toBe(87900);
  });
  it("parseWon은_소수와_음수를_null로", () => {
    expect(parseWon("1.5")).toBeNull();
    expect(parseWon("-100")).toBeNull();
    expect(parseWon("")).toBeNull();
    expect(parseWon("27,300")).toBe(27300);
  });
  it("안전_정수가_아니면_개발에서_throw", () => {
    expect(() => assertSafeInt(0.1)).toThrow();
    expect(() => formatKrw(1.5)).toThrow();
    expect(() => assertSafeInt(Number.MAX_SAFE_INTEGER + 1)).toThrow();
  });
  it("formatBp(1000)은_10퍼센트", () => {
    expect(formatBp(1000)).toBe("10%");
    expect(formatBp(1250)).toBe("12.5%");
    expect(formatBp(1205)).toBe("12.05%");
  });
});
