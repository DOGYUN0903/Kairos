import { describe, expect, it } from "vitest";
import { parseCouponStatus, parseCouponTab } from "@/features/coupons/coupon-params";

const sp = (q: string) => new URLSearchParams(q);

describe("parseCouponTab", () => {
  it("값이 없으면 받을 수 있는 쿠폰 탭이다", () => {
    expect(parseCouponTab(sp(""))).toBe("claimable");
  });
  it("mine 은 내 쿠폰 탭이다", () => {
    expect(parseCouponTab(sp("tab=mine"))).toBe("mine");
  });
  it("모르는 값은 기본 탭으로 되돌린다", () => {
    expect(parseCouponTab(sp("tab=other"))).toBe("claimable");
    expect(parseCouponTab(sp("tab=MINE"))).toBe("claimable");
  });
});

describe("parseCouponStatus", () => {
  it("값이 없으면 사용 가능이다", () => {
    expect(parseCouponStatus(sp(""))).toBe("AVAILABLE");
  });
  it("USED·EXPIRED 를 받는다", () => {
    expect(parseCouponStatus(sp("status=USED"))).toBe("USED");
    expect(parseCouponStatus(sp("status=EXPIRED"))).toBe("EXPIRED");
  });
  it("모르는 값은 기본 상태로 되돌린다", () => {
    expect(parseCouponStatus(sp("status=used"))).toBe("AVAILABLE");
    expect(parseCouponStatus(sp("status=ALL"))).toBe("AVAILABLE");
  });
});
