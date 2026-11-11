import { describe, expect, it } from "vitest";
import { loginSchema, signupSchema } from "@/features/auth/schemas";

describe("auth schemas", () => {
  it("비밀번호_7자는_거부_8자는_통과", () => {
    expect(loginSchema.safeParse({ email: "a@b.co", password: "1234567" }).success).toBe(false);
    expect(loginSchema.safeParse({ email: "a@b.co", password: "12345678" }).success).toBe(true);
  });

  it("이메일_형식_검사", () => {
    expect(loginSchema.safeParse({ email: "not-email", password: "12345678" }).success).toBe(false);
  });

  it("가입_비밀번호는_72자까지_이름은_1자_이상_50자_이하", () => {
    const ok = { email: "a@b.co", password: "12345678", name: "김민지" };
    expect(signupSchema.safeParse(ok).success).toBe(true);
    expect(signupSchema.safeParse({ ...ok, password: "a".repeat(73) }).success).toBe(false);
    expect(signupSchema.safeParse({ ...ok, name: "" }).success).toBe(false);
    expect(signupSchema.safeParse({ ...ok, name: "가".repeat(51) }).success).toBe(false);
  });
});
