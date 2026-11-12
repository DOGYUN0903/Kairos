import { describe, expect, it } from "vitest";
import { generateResultText, validatePeriodStart, weekLastDay } from "@/features/admin/settlements/generate";

// 2026-11-12(목) 낮 KST. 가장 최근에 끝난 주의 월요일은 2026-11-02.
const NOW = Date.parse("2026-11-12T10:00:00+09:00");

describe("validatePeriodStart", () => {
  it("화요일이면_NOT_MONDAY", () => expect(validatePeriodStart("2026-11-03", NOW)).toBe("NOT_MONDAY"));
  it("이번_주_월요일이면_NOT_FINISHED", () => expect(validatePeriodStart("2026-11-09", NOW)).toBe("NOT_FINISHED"));
  it("다음_주_월요일이면_NOT_FINISHED", () => expect(validatePeriodStart("2026-11-16", NOW)).toBe("NOT_FINISHED"));
  it("지난주_월요일이면_통과", () => expect(validatePeriodStart("2026-11-02", NOW)).toBeNull());
  it("더_오래된_월요일도_통과", () => expect(validatePeriodStart("2026-10-05", NOW)).toBeNull());
  it("빈_값이면_INVALID", () => expect(validatePeriodStart("", NOW)).toBe("INVALID"));
  it("형식이_틀리면_INVALID", () => {
    expect(validatePeriodStart("2026/11/02", NOW)).toBe("INVALID");
    expect(validatePeriodStart("26-11-02", NOW)).toBe("INVALID");
    expect(validatePeriodStart("2026-02-30", NOW)).toBe("INVALID");
  });
});

const base = { periodStart: "2026-11-02", periodEnd: "2026-11-09", sellerCount: 3, createdCount: 3, skippedCount: 0, failedCount: 0, alreadyGenerated: false };

describe("generateResultText", () => {
  it("이미_생성된_기간은_info", () => {
    const t = generateResultText({ ...base, alreadyGenerated: true, createdCount: 0, skippedCount: 3 });
    expect(t.tone).toBe("info");
    expect(t.lines).toEqual(["이미 생성된 기간입니다"]);
  });
  it("실패가_있으면_warning", () => {
    const t = generateResultText({ ...base, createdCount: 2, failedCount: 1 });
    expect(t.tone).toBe("warning");
    expect(t.lines).toEqual(["생성 2 · 건너뜀 0 · 실패 1", "다시 생성하면 실패한 판매자만 만들어집니다"]);
  });
  it("그_밖에는_success", () => {
    const t = generateResultText({ ...base, createdCount: 2, skippedCount: 1 });
    expect(t.tone).toBe("success");
    expect(t.lines).toEqual(["대상 판매자 3명 · 생성 2 · 건너뜀 1"]);
  });
});

describe("weekLastDay", () => {
  it("월요일이_시작하는_주의_일요일", () => {
    expect(weekLastDay("2026-11-02")).toBe("2026-11-08");
    expect(weekLastDay("2026-12-28")).toBe("2027-01-03");
  });
});
