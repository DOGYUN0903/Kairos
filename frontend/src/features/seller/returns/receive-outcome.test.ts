import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/client";
import { receiveOutcome } from "./receive-outcome";

describe("receiveOutcome", () => {
  it("COMPLETED면_환불_완료와_금액이다", () => {
    expect(receiveOutcome({ ok: true, status: "COMPLETED", refundAmount: 14000 })).toEqual({ kind: "completed", refundAmount: 14000 });
  });

  it("REFUNDING이면_환불_확인_중이다", () => {
    expect(receiveOutcome({ ok: true, status: "REFUNDING", refundAmount: 0 })).toEqual({ kind: "refunding" });
  });

  it("PAYMENT_CANCEL_FAILED는_환불_거절이다", () => {
    expect(receiveOutcome({ ok: false, error: new ApiError(409, "PAYMENT_CANCEL_FAILED", "PG 거절", null) })).toEqual({ kind: "rejected" });
  });

  it("네트워크_오류는_결과를_모른다", () => {
    expect(receiveOutcome({ ok: false, error: new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null) })).toEqual({ kind: "unknown" });
  });

  it("5xx는_결과를_모른다", () => {
    expect(receiveOutcome({ ok: false, error: new ApiError(502, "INTERNAL_ERROR", "bad gateway", null) })).toEqual({ kind: "unknown" });
    expect(receiveOutcome({ ok: false, error: new ApiError(500, "INTERNAL_ERROR", "서버 오류", null) })).toEqual({ kind: "unknown" });
  });

  it("ApiError가_아닌_오류도_결과를_모른다", () => {
    expect(receiveOutcome({ ok: false, error: new TypeError("Failed to fetch") })).toEqual({ kind: "unknown" });
  });

  it("IDEMPOTENCY_IN_PROGRESS는_결과를_모른다", () => {
    expect(receiveOutcome({ ok: false, error: new ApiError(409, "IDEMPOTENCY_IN_PROGRESS", "처리 중", null) })).toEqual({ kind: "unknown" });
  });

  it("그_밖의_4xx는_서버_메시지다", () => {
    expect(receiveOutcome({ ok: false, error: new ApiError(409, "RETURN_INVALID_STATE_TRANSITION", "이미 처리된 반품입니다", null) })).toEqual({
      kind: "error",
      message: "이미 처리된 반품입니다",
    });
  });

  it("예상하지_못한_성공_상태는_결과를_모른다", () => {
    expect(receiveOutcome({ ok: true, status: "APPROVED", refundAmount: 0 })).toEqual({ kind: "unknown" });
  });
});
