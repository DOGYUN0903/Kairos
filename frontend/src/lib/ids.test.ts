import { describe, expect, it } from "vitest";
import { parseId } from "@/lib/ids";

describe("parseId", () => {
  it("양의_정수_문자열은_숫자로_읽는다", () => {
    expect(parseId("1")).toBe(1);
    expect(parseId("42")).toBe(42);
    expect(parseId("9007199254740991")).toBe(Number.MAX_SAFE_INTEGER);
  });

  it("양의_정수가_아니면_null이다", () => {
    for (const raw of ["0", "abc", "1.5", "-3", "", " 1", "1 ", "01", "1e3", "+1", "0x10"]) {
      expect(parseId(raw), raw).toBeNull();
    }
    expect(parseId(null)).toBeNull();
    expect(parseId(undefined)).toBeNull();
  });

  it("안전한_정수_범위를_넘으면_null이다", () => {
    // 2^53 과 그보다 큰 16자리 값은 Number 로 정확히 표현되지 않는다.
    expect(parseId("9007199254740992")).toBeNull();
    expect(parseId("9999999999999999")).toBeNull();
    expect(parseId("12345678901234567890")).toBeNull();
  });
});
