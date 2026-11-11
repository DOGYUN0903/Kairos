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
});
