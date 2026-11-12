import { describe, expect, it } from "vitest";
import { expiredRecheckInterval, isPastExpiry, shouldRefetchAtExpiry } from "@/features/orders/expiry";

describe("만료 재조회", () => {
  it("결제_대기이면서_남은_시간이_0이면_기한이_지난_것이다", () => {
    expect(isPastExpiry("PENDING_PAYMENT", 0)).toBe(true);
    expect(isPastExpiry("PENDING_PAYMENT", 5)).toBe(false);
    expect(isPastExpiry("CANCELED", 0)).toBe(false);
    expect(isPastExpiry("PAID", 0)).toBe(false);
  });
  it("만료_시각을_넘는_순간_한_번만_재조회한다", () => {
    expect(shouldRefetchAtExpiry(false, true)).toBe(true);
    expect(shouldRefetchAtExpiry(true, true)).toBe(false);
    expect(shouldRefetchAtExpiry(false, false)).toBe(false);
    expect(shouldRefetchAtExpiry(true, false)).toBe(false);
  });
  it("만료된_결제_대기_주문은_상태가_바뀔_때까지_천천히_다시_확인한다", () => {
    expect(expiredRecheckInterval(true, 0)).toBe(10000);
    expect(expiredRecheckInterval(true, 60000)).toBe(10000);
    expect(expiredRecheckInterval(false, 0)).toBe(false);
  });
  it("만료_재확인은_2분_뒤_멈춘다", () => {
    expect(expiredRecheckInterval(true, 119999)).toBe(10000);
    expect(expiredRecheckInterval(true, 120000)).toBe(false);
  });
});
