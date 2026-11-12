import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";

describe("resolveError", () => {
  it("MEMBER_EMAIL_DUPLICATED는_email_필드", () => {
    const r = resolveError(new ApiError(409, "MEMBER_EMAIL_DUPLICATED", "이미 가입된 이메일", "t-1"));
    expect(r).toEqual({ kind: "field", field: "email", message: "이미 가입된 이메일", traceId: "t-1" });
  });
  it("SELLER_NOT_ACTIVE는_kind_banner", () => {
    expect(resolveError(new ApiError(403, "SELLER_NOT_ACTIVE", "x", null)).kind).toBe("banner");
  });
  it("표에_없는_코드는_서버_메시지를_토스트로", () => {
    expect(resolveError(new ApiError(409, "STOCK_SOLD_OUT", "품절입니다", null))).toEqual({
      kind: "toast",
      message: "품절입니다",
      traceId: null,
    });
  });
  it("ApiError가_아니면_INTERNAL_ERROR_토스트", () => {
    expect(resolveError(new TypeError("boom"))).toEqual({
      kind: "toast",
      message: "잠시 후 다시 시도해 주세요.",
      traceId: null,
    });
  });
  it("주문_없음은_page_ORDER_INVALID는_form", () => {
    expect(resolveError(new ApiError(404, "ORDER_NOT_FOUND", "x", null)).kind).toBe("page");
    expect(resolveError(new ApiError(404, "SELLER_ORDER_NOT_FOUND", "x", null)).kind).toBe("page");
    expect(resolveError(new ApiError(422, "ORDER_INVALID", "x", null)).kind).toBe("form");
  });
});

describe("resolveError F3", () => {
  it("후기·쿠폰_정책_양식_오류는_form", () => {
    expect(resolveError(new ApiError(400, "REVIEW_INVALID", "x", null)).kind).toBe("form");
    expect(resolveError(new ApiError(400, "COUPON_POLICY_INVALID", "x", null)).kind).toBe("form");
  });
  it("REVIEW_NOT_FOUND는_toast_서버_메시지", () => {
    expect(resolveError(new ApiError(404, "REVIEW_NOT_FOUND", "후기 없음", null))).toEqual({
      kind: "toast",
      message: "후기 없음",
      traceId: null,
    });
  });
  it("쿠폰_소진·종료_메시지", () => {
    expect(resolveError(new ApiError(409, "COUPON_SOLD_OUT", "x", null)).message).toBe("쿠폰이 모두 소진되었습니다.");
    expect(resolveError(new ApiError(409, "COUPON_POLICY_ENDED", "x", null)).message).toBe("종료된 쿠폰입니다.");
  });
  it("F4_반품·정산_오류", () => {
    expect(resolveError(new ApiError(404, "RETURN_NOT_FOUND", "x", null)).kind).toBe("page");
    expect(resolveError(new ApiError(404, "SETTLEMENT_NOT_FOUND", "x", null)).kind).toBe("page");
    expect(resolveError(new ApiError(400, "RETURN_INVALID", "x", null)).kind).toBe("form");
    expect(resolveError(new ApiError(400, "SETTLEMENT_ADJUSTMENT_INVALID", "x", null)).kind).toBe("form");
    expect(resolveError(new ApiError(400, "SETTLEMENT_PERIOD_INVALID", "x", null)).kind).toBe("form");
  });
});
