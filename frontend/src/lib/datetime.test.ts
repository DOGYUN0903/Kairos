import { afterEach, describe, expect, it, vi } from "vitest";
import { formatCountdown, formatDate, formatDateTime, secondsUntilKst, toLocalDateTimeString } from "@/lib/datetime";

describe("datetime", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("LocalDateTime_문자열을_Date_없이_표시한다", () => {
    vi.stubGlobal("Date", function () {
      throw new Error("Date 사용 금지");
    });
    expect(formatDateTime("2026-11-07T14:20:00")).toBe("2026-11-07 14:20");
    expect(formatDate("2026-11-07T14:20:00")).toBe("2026-11-07");
    expect(formatDate("2026-11-07")).toBe("2026-11-07");
    expect(toLocalDateTimeString({ y: 2026, m: 1, d: 5, hh: 9, mm: 3 })).toBe("2026-01-05T09:03:00");
  });

  it("소수_초가_붙은_DATETIME(6)_값도_표시한다", () => {
    expect(formatDateTime("2026-11-07T14:20:00.123456")).toBe("2026-11-07 14:20");
    expect(formatDateTime("2026-11-07T14:20")).toBe("2026-11-07 14:20");
  });
});

describe("secondsUntilKst", () => {
  const now = Date.parse("2026-11-12T10:00:00+09:00");
  it("남은_초를_센다", () => {
    expect(secondsUntilKst("2026-11-12T10:15:00", now)).toBe(900);
  });
  it("지난_시각은_0", () => {
    expect(secondsUntilKst("2026-11-12T09:00:00", now)).toBe(0);
  });
  it("소수_초는_잘라낸다", () => {
    expect(secondsUntilKst("2026-11-12T10:15:00.123456", now)).toBe(900);
  });
  it("초가_없는_값도_읽는다", () => {
    // 백엔드(Jackson)는 초·소수 초가 0 이면 초를 생략할 수 있다. 0 을 돌려주면 결제 기한이 지난 것으로 처리돼 결제가 막힌다.
    expect(secondsUntilKst("2026-11-12T10:15", now)).toBe(900);
  });
  it("소수_7~9자리(나노초)도_읽는다", () => {
    expect(secondsUntilKst("2026-11-12T10:15:00.1234567", now)).toBe(900);
    expect(secondsUntilKst("2026-11-12T10:15:30.123456789", now)).toBe(930);
  });
  it("형식이_아니면_0", () => {
    expect(secondsUntilKst("2026-11-12", now)).toBe(0);
    expect(secondsUntilKst("2026-11-12T10:15:00+09:00", now)).toBe(0);
    expect(secondsUntilKst("", now)).toBe(0);
  });
});

describe("formatCountdown", () => {
  it("MM:SS로_만든다", () => {
    expect(formatCountdown(754)).toBe("12:34");
    expect(formatCountdown(0)).toBe("00:00");
  });
});
