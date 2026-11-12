import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/client";
import { isAnswered, newIdempotencyKey } from "@/api/idempotency";

describe("idempotency", () => {
  it("키는_UUID_v4_형식", () => {
    const a = newIdempotencyKey();
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(newIdempotencyKey()).not.toBe(a);
  });
});

describe("isAnswered", () => {
  it("서버가_4xx로_답한_오류는_true", () => {
    for (const status of [409, 402, 422]) expect(isAnswered(new ApiError(status, "INVALID_REQUEST", "x", null))).toBe(true);
  });
  it("status_0과_5xx와_ApiError가_아닌_오류는_false", () => {
    expect(isAnswered(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null))).toBe(false);
    expect(isAnswered(new ApiError(500, "INTERNAL_ERROR", "x", null))).toBe(false);
    expect(isAnswered(new ApiError(503, "INTERNAL_ERROR", "x", null))).toBe(false);
    expect(isAnswered(new Error("x"))).toBe(false);
  });
});
