import { describe, expect, it } from "vitest";
import { pointsDisabledReason, pointsMessage, validatePoints } from "@/features/checkout/points-input";

describe("validatePoints", () => {
  it("빈_문자열은_0이다", () => {
    expect(validatePoints("", 5000)).toEqual({ ok: true, points: 0 });
  });
  it("한도_이내의_10P_단위는_통과한다", () => {
    expect(validatePoints("1000", 5000)).toEqual({ ok: true, points: 1000 });
    expect(validatePoints("5000", 5000)).toEqual({ ok: true, points: 5000 });
    expect(validatePoints("0", 5000)).toEqual({ ok: true, points: 0 });
  });
  it("10P_단위가_아니면_NOT_UNIT이다", () => {
    expect(validatePoints("15", 5000)).toEqual({ ok: false, reason: "NOT_UNIT" });
  });
  it.each(["-10", "1e3", " 100", "10.0", "abc", "  ", "1,000"])("숫자가_아니면_NOT_NUMBER다 (%j)", (raw) => {
    expect(validatePoints(raw, 5000)).toEqual({ ok: false, reason: "NOT_NUMBER" });
  });
  it("한도를_넘으면_OVER_MAX다", () => {
    expect(validatePoints("5010", 5000)).toEqual({ ok: false, reason: "OVER_MAX" });
  });
  it("안전하지_않은_큰_수는_NOT_NUMBER다", () => {
    expect(validatePoints("99999999999999999999", 5000)).toEqual({ ok: false, reason: "NOT_NUMBER" });
  });
});

describe("pointsMessage", () => {
  it("이유별_문구", () => {
    expect(pointsMessage("NOT_NUMBER", 17700)).toBe("숫자만 입력해 주세요");
    expect(pointsMessage("NOT_UNIT", 17700)).toBe("10P 단위로 입력해 주세요");
    expect(pointsMessage("OVER_MAX", 17700)).toBe("최대 17,700P까지 사용할 수 있습니다");
  });
});

describe("pointsDisabledReason", () => {
  it("쓸_수_없는_이유를_돌려준다", () => {
    expect(pointsDisabledReason(0, 0)).toBe("보유 포인트가 없습니다");
    expect(pointsDisabledReason(900, 0)).toBe("이 주문에는 포인트를 사용할 수 없습니다 (1,000P 이상 보유 시, 상품 금액의 30%까지)");
    expect(pointsDisabledReason(5000, 0)).toBe("이 주문에는 포인트를 사용할 수 없습니다 (1,000P 이상 보유 시, 상품 금액의 30%까지)");
    expect(pointsDisabledReason(5000, 1000)).toBeNull();
  });
});
