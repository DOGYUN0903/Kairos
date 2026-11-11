import { describe, expect, it } from "vitest";
import { categorySchema } from "./schemas";

describe("categorySchema", () => {
  it("displayOrder_음수는_거부한다", () => {
    const r = categorySchema.safeParse({ name: "상의", displayOrder: -1 });
    expect(r.success).toBe(false);
  });
  it("displayOrder_소수와_NaN은_거부한다", () => {
    expect(categorySchema.safeParse({ name: "상의", displayOrder: 1.5 }).success).toBe(false);
    expect(categorySchema.safeParse({ name: "상의", displayOrder: Number.NaN }).success).toBe(false);
  });
  it("이름_공백은_거부한다", () => {
    expect(categorySchema.safeParse({ name: "   ", displayOrder: 0 }).success).toBe(false);
  });
  it("이름은_앞뒤_공백을_제거하고_50자까지_허용한다", () => {
    const ok = categorySchema.safeParse({ name: "  상의  ", displayOrder: 0 });
    expect(ok.success && ok.data.name).toBe("상의");
    expect(categorySchema.safeParse({ name: "가".repeat(50), displayOrder: 0 }).success).toBe(true);
    expect(categorySchema.safeParse({ name: "가".repeat(51), displayOrder: 0 }).success).toBe(false);
  });
});
