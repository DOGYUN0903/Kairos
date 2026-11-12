import { describe, expect, it } from "vitest";
import { parseMemberIdParam } from "./params";

const sp = (s: string) => new URLSearchParams(s);

describe("parseMemberIdParam", () => {
  it("양의_정수는_숫자로_읽는다", () => {
    expect(parseMemberIdParam(sp("memberId=12"))).toBe(12);
  });

  it("0_음수_문자_소수_없음은_null이다", () => {
    for (const q of ["memberId=0", "memberId=-1", "memberId=abc", "memberId=1.5", ""]) {
      expect(parseMemberIdParam(sp(q))).toBeNull();
    }
  });
});
