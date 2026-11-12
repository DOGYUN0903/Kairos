import { describe, expect, it } from "vitest";
import { ApiError } from "@/api/client";
import { claimOutcome } from "@/features/coupons/claim-error";

const err = (code: ConstructorParameters<typeof ApiError>[1], message = "서버 메시지") => new ApiError(409, code, message, null);

describe("claimOutcome", () => {
  it("이미 받은 쿠폰은 안내 문구와 함께 목록을 다시 읽게 한다", () => {
    expect(claimOutcome(err("COUPON_ALREADY_ISSUED"))).toEqual({ message: "이미 받은 쿠폰입니다.", refetch: true });
  });
  it("소진된 쿠폰은 안내 문구와 함께 목록을 다시 읽게 한다", () => {
    expect(claimOutcome(err("COUPON_SOLD_OUT"))).toEqual({ message: "쿠폰이 모두 소진되었습니다.", refetch: true });
  });
  it("종료된 쿠폰은 안내 문구와 함께 목록을 다시 읽게 한다", () => {
    expect(claimOutcome(err("COUPON_POLICY_ENDED"))).toEqual({ message: "종료된 쿠폰입니다.", refetch: true });
  });
  it("그 밖의 오류는 오류 표의 메시지를 쓰고 다시 읽지 않는다", () => {
    expect(claimOutcome(err("IDEMPOTENCY_IN_PROGRESS"))).toEqual({
      message: "같은 요청이 처리 중입니다. 잠시 후 확인하세요.",
      refetch: false,
    });
    expect(claimOutcome(err("COUPON_POLICY_NOT_FOUND", "정책이 없습니다"))).toEqual({ message: "정책이 없습니다", refetch: false });
  });
  it("ApiError 가 아니면 기본 문구를 쓴다", () => {
    expect(claimOutcome(new Error("boom"))).toEqual({ message: "잠시 후 다시 시도해 주세요.", refetch: false });
  });
});
