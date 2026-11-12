import { describe, expect, it } from "vitest";
import { parsePage } from "@/lib/page";

describe("page 파라미터", () => {
  it("없거나_숫자가_아니면_0이다", () => {
    expect(parsePage(null)).toBe(0);
    expect(parsePage("")).toBe(0);
    expect(parsePage("abc")).toBe(0);
    expect(parsePage("1.5")).toBe(0);
  });
  it("음수는_0이다", () => {
    expect(parsePage("-1")).toBe(0);
  });
  it("양의_정수는_그대로다", () => {
    expect(parsePage("0")).toBe(0);
    expect(parsePage("3")).toBe(3);
    expect(parsePage("123456789")).toBe(123456789);
  });
  it("자릿수가_너무_많으면_0이다", () => {
    expect(parsePage("1234567890")).toBe(0);
  });
});
