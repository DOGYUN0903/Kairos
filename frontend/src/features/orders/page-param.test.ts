import { describe, expect, it } from "vitest";
import { parsePage } from "@/features/orders/page-param";

describe("주문 내역 page 파라미터", () => {
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
  });
});
