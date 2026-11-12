import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/client";
import { previewBlocksRequest } from "@/features/returns/api";

describe("previewBlocksRequest", () => {
  it.each(["RETURN_NOT_ELIGIBLE", "RETURN_WINDOW_EXPIRED", "RETURN_ALREADY_REQUESTED"] as const)("%s는_요청을_막는다", (code) => {
    expect(previewBlocksRequest(new ApiError(409, code, "서버 메시지", null))).toBe(true);
  });

  it("네트워크_오류_5xx_모르는_코드는_요청을_막지_않는다", () => {
    expect(previewBlocksRequest(new ApiError(0, "INTERNAL_ERROR", "네트워크 오류", null))).toBe(false);
    expect(previewBlocksRequest(new ApiError(500, "INTERNAL_ERROR", "오류", null))).toBe(false);
    expect(previewBlocksRequest(new ApiError(409, "IDEMPOTENCY_IN_PROGRESS", "처리 중", null))).toBe(false);
    expect(previewBlocksRequest(new Error("x"))).toBe(false);
    expect(previewBlocksRequest(null)).toBe(false);
  });
});
