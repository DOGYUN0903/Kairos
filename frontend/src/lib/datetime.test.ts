import { afterEach, describe, expect, it, vi } from "vitest";
import { formatDate, formatDateTime, toLocalDateTimeString } from "@/lib/datetime";

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
