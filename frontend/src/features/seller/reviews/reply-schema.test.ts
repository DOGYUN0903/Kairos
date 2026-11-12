import { describe, expect, it } from "vitest";
import { replySchema } from "@/features/seller/reviews/reply-schema";

describe("replySchema", () => {
  it("공백만 입력하면 실패한다", () => {
    expect(replySchema.safeParse({ content: "    " }).success).toBe(false);
  });
  it("500자는 통과한다", () => {
    expect(replySchema.safeParse({ content: "가".repeat(500) }).success).toBe(true);
  });
  it("501자는 실패한다", () => {
    expect(replySchema.safeParse({ content: "가".repeat(501) }).success).toBe(false);
  });
  it("앞뒤 공백은 잘라 낸다", () => {
    const r = replySchema.safeParse({ content: "  감사합니다  " });
    expect(r.success && r.data.content).toBe("감사합니다");
  });
});
