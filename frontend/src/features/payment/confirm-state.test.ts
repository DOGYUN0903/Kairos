import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { parseConfirmParams, toConfirmView, type ConfirmPaymentResponse } from "@/features/payment/confirm-state";
import { recallOrderId, rememberOrderRef } from "@/features/payment/order-ref-store";

const ORDER_NO = "K20261112142110Q3ZT8B";

function ok(paymentStatus: ConfirmPaymentResponse["paymentStatus"], orderStatus: ConfirmPaymentResponse["orderStatus"]) {
  return toConfirmView({ ok: true, data: { orderId: 42, orderNo: ORDER_NO, orderStatus, paymentStatus } });
}
function fail(error: unknown) {
  return toConfirmView({ ok: false, error });
}
const q = (s: string) => new URLSearchParams(s);

describe("toConfirmView", () => {
  it("APPROVED면_승인이고_응답의_주문_ID를_쓴다", () => {
    expect(ok("APPROVED", "PAID")).toEqual({ kind: "approved", orderId: 42 });
  });

  it("UNKNOWN이면_확인_중이다", () => {
    expect(ok("UNKNOWN", "PENDING_PAYMENT")).toEqual({ kind: "pending" });
  });

  it("200인데_승인도_UNKNOWN도_아니면_단정하지_않고_확인_중이다", () => {
    expect(ok("IN_PROGRESS", "PENDING_PAYMENT")).toEqual({ kind: "pending" });
  });

  it("409_PAYMENT_IN_PROGRESS면_확인_중이다", () => {
    expect(fail(new ApiError(409, "PAYMENT_IN_PROGRESS", "진행 중", null))).toEqual({ kind: "pending" });
  });

  it("409_IDEMPOTENCY_IN_PROGRESS면_확인_중이다", () => {
    expect(fail(new ApiError(409, "IDEMPOTENCY_IN_PROGRESS", "처리 중", null))).toEqual({ kind: "pending" });
  });

  it("네트워크_오류면_확인_중이다", () => {
    expect(fail(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null))).toEqual({ kind: "pending" });
  });

  it("5xx면_확인_중이다", () => {
    expect(fail(new ApiError(500, "INTERNAL_ERROR", "서버 오류", null))).toEqual({ kind: "pending" });
    expect(fail(new ApiError(503, "INTERNAL_ERROR", "서버 오류", null))).toEqual({ kind: "pending" });
  });

  it("ApiError가_아닌_오류면_확인_중이다", () => {
    expect(fail(new TypeError("boom"))).toEqual({ kind: "pending" });
    expect(fail(undefined)).toEqual({ kind: "pending" });
  });

  it("402_PAYMENT_REJECTED면_거절이다", () => {
    expect(fail(new ApiError(402, "PAYMENT_REJECTED", "카드사 거절", null))).toEqual({ kind: "rejected" });
  });

  it("그_밖의_서버_응답은_서버_메시지를_보여주는_오류다", () => {
    expect(fail(new ApiError(400, "PAYMENT_AMOUNT_MISMATCH", "결제 금액이 주문 금액과 다릅니다", null))).toEqual({
      kind: "error",
      message: "결제 금액이 주문 금액과 다릅니다",
    });
    expect(fail(new ApiError(409, "ORDER_INVALID_STATE_TRANSITION", "결제할 수 없는 주문입니다", null))).toEqual({
      kind: "error",
      message: "결제할 수 없는 주문입니다",
    });
    expect(fail(new ApiError(409, "PAYMENT_KEY_DUPLICATED", "이미 사용된 결제 키입니다", null))).toEqual({
      kind: "error",
      message: "이미 사용된 결제 키입니다",
    });
    expect(fail(new ApiError(404, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다", null))).toEqual({
      kind: "error",
      message: "주문을 찾을 수 없습니다",
    });
  });
});

describe("parseConfirmParams", () => {
  beforeEach(() => {
    sessionStorage.clear();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("쿼리를_승인_요청_값으로_읽는다", () => {
    expect(parseConfirmParams(q(`oid=42&orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`))).toEqual({
      orderNo: ORDER_NO,
      paymentKey: "mock-a",
      amount: 48000,
      orderId: 42,
    });
  });

  it("쿼리가_빠지거나_amount가_숫자가_아니면_null이다", () => {
    expect(parseConfirmParams(q(`oid=42&paymentKey=mock-a&amount=48000`))).toBeNull();
    expect(parseConfirmParams(q(`oid=42&orderId=${ORDER_NO}&amount=48000`))).toBeNull();
    expect(parseConfirmParams(q(`oid=42&orderId=${ORDER_NO}&paymentKey=mock-a`))).toBeNull();
    expect(parseConfirmParams(q(`oid=42&orderId=&paymentKey=mock-a&amount=48000`))).toBeNull();
    for (const amount of ["abc", "48000.5", "-1", "1e3", "", " 48000", "99999999999999999999"]) {
      expect(parseConfirmParams(q(`oid=42&orderId=${ORDER_NO}&paymentKey=mock-a&amount=${encodeURIComponent(amount)}`))).toBeNull();
    }
  });

  it("oid가_없으면_저장해_둔_주문_ID를_쓴다", () => {
    rememberOrderRef(ORDER_NO, 77);
    expect(parseConfirmParams(q(`orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`))?.orderId).toBe(77);
    // 잘못된 oid 도 없는 것으로 본다.
    expect(parseConfirmParams(q(`oid=abc&orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`))?.orderId).toBe(77);
  });

  it("oid가_안전한_정수가_아니면_저장해_둔_주문_ID를_쓴다", () => {
    rememberOrderRef(ORDER_NO, 77);
    // 16자리지만 Number.MAX_SAFE_INTEGER 를 넘는 값은 주문 ID 로 쓰지 않는다(조회가 꺼진 채 멈춘다).
    for (const oid of ["9007199254740992", "9999999999999999", "0", "1.5"]) {
      expect(parseConfirmParams(q(`oid=${oid}&orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`))?.orderId, oid).toBe(77);
    }
    expect(parseConfirmParams(q(`oid=9007199254740991&orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`))?.orderId).toBe(Number.MAX_SAFE_INTEGER);
  });

  it("oid도_저장된_값도_없으면_주문_ID는_null이다", () => {
    expect(parseConfirmParams(q(`orderId=${ORDER_NO}&paymentKey=mock-a&amount=48000`))?.orderId).toBeNull();
  });

  it("sessionStorage가_막혀도_주문_ID_저장과_조회가_예외를_던지지_않는다", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("blocked", "SecurityError");
    });
    expect(() => rememberOrderRef(ORDER_NO, 77)).not.toThrow();
    expect(recallOrderId(ORDER_NO)).toBeNull();
  });
});
