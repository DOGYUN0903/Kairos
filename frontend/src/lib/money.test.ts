import { describe, expect, it } from "vitest";
import { percentToBp, amountUntil, assertSafeInt, discountRate, floorDiv, formatBp, formatKrw, lineTotal, mod, unitPrice, parseWon } from "@/lib/money";

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
  it("unitPrice는_판매가와_추가금의_합이다",()=>{
    expect(unitPrice(27300, 2000)).toBe(29300);
    expect(unitPrice(27300, 0)).toBe(27300);
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

describe("amountUntil", () => {
  it("기준까지_남은_금액", () => {
    expect(amountUntil(50000, 42000)).toBe(8000);
  });
  it("기준과_같거나_넘으면_0", () => {
    expect(amountUntil(50000, 50000)).toBe(0);
    expect(amountUntil(50000, 61000)).toBe(0);
  });
});

describe("floorDiv와 mod", () => {
  it("0_이하_제수는_던진다", () => {
    expect(() => floorDiv(10, 0)).toThrow();
    expect(() => mod(10, -3)).toThrow();
  });
  it("floorDiv와_mod는_음수에서도_내림_기준", () => {
    expect(floorDiv(754, 60)).toBe(12);
    expect(mod(754, 60)).toBe(34);
    expect(floorDiv(-1, 60)).toBe(-1);
    expect(mod(-1, 60)).toBe(59);
  });
});

describe("percentToBp", () => {
  it("정수_퍼센트를_bp로_바꾼다", () => {
    expect(percentToBp(10)).toBe(1000);
    expect(percentToBp(1)).toBe(100);
    expect(percentToBp(100)).toBe(10000);
  });
  it("범위_밖이거나_정수가_아니면_throw", () => {
    for (const bad of [0, 101, 10.5, Number.NaN]) expect(() => percentToBp(bad)).toThrow();
  });
});
