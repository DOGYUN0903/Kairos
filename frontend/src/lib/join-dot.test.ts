import { describe, expect, it } from "vitest";
import { joinDot } from "@/lib/join-dot";

describe("joinDot", () => {
  it("값이 있으면 구분점으로 잇는다", () => {
    expect(joinDot("블랙 / M", "수량 1개")).toBe("블랙 / M · 수량 1개");
  });
  it("빈 옵션은 건너뛴다", () => {
    expect(joinDot("", "수량 1개")).toBe("수량 1개");
    expect(joinDot(null, "수량 1개")).toBe("수량 1개");
    expect(joinDot(undefined, "수량 1개")).toBe("수량 1개");
  });
  it("모두 비면 빈 문자열", () => {
    expect(joinDot("", null)).toBe("");
  });
});
