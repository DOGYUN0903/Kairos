import { describe, expect, it } from "vitest";
import { isExpiringSoon } from "@/features/coupons/expiry";

describe("isExpiringSoon", () => {
  const now = Date.parse("2026-11-12T10:00:00+09:00");
  it("정확히_7일_남으면_true", () => {
    expect(isExpiringSoon("2026-11-19T10:00:00", now)).toBe(true);
  });
  it("7일_1초_남으면_false", () => {
    expect(isExpiringSoon("2026-11-19T10:00:01", now)).toBe(false);
  });
  it("이미_지났으면_false", () => {
    expect(isExpiringSoon("2026-11-12T10:00:00", now)).toBe(false);
    expect(isExpiringSoon("2026-11-11T10:00:00", now)).toBe(false);
  });
});
