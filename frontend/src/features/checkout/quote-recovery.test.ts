import { describe, expect, it } from "vitest";
import { EMPTY_SELECTION, type BenefitSelection } from "@/features/checkout/benefit-selection";
import { recoverSelection } from "@/features/checkout/quote-recovery";

const sel = (over: Partial<BenefitSelection> = {}): BenefitSelection => ({ ...EMPTY_SELECTION, ...over });

describe("recoverSelection", () => {
  it("포인트_오류면_포인트만_0으로_되돌린다", () => {
    const current = sel({ productCoupons: { 1: 10 }, cartCouponId: 3, points: 2000 });
    const r = recoverSelection("POINT_EXCEEDS_LIMIT", current, EMPTY_SELECTION);
    expect(r?.next).toEqual({ productCoupons: { 1: 10 }, cartCouponId: 3, points: 0 });
    expect(r?.notice).toBe("쿠폰이 바뀌어 포인트 사용을 초기화했습니다.");
  });

  it.each(["POINT_EXCEEDS_LIMIT", "POINT_INSUFFICIENT", "POINT_INVALID_AMOUNT"])("포인트_코드_%s를_처리한다", (code) => {
    expect(recoverSelection(code, sel({ points: 100 }), EMPTY_SELECTION)?.next.points).toBe(0);
  });

  it.each(["COUPON_MIN_AMOUNT_NOT_MET", "COUPON_NOT_APPLICABLE", "COUPON_NOT_AVAILABLE", "COUPON_NOT_FOUND"])("쿠폰_오류_%s면_직전_성공_선택으로_되돌린다", (code) => {
    const lastGood = sel({ productCoupons: { 1: 10 } });
    const current = sel({ productCoupons: { 1: 10, 2: 11 } });
    const r = recoverSelection(code, current, lastGood);
    expect(r?.next).toEqual(lastGood);
    expect(r?.notice).toBe("쿠폰을 사용할 수 없어 선택을 되돌렸습니다.");
  });

  it("직전_선택과_같으면_전부_비운다", () => {
    const current = sel({ productCoupons: { 1: 10 }, points: 1000 });
    expect(recoverSelection("COUPON_NOT_AVAILABLE", current, current)?.next).toEqual(EMPTY_SELECTION);
  });

  it("이미_빈_선택이면_null이다", () => {
    expect(recoverSelection("COUPON_NOT_AVAILABLE", EMPTY_SELECTION, EMPTY_SELECTION)).toBeNull();
    expect(recoverSelection("COUPON_NOT_AVAILABLE", EMPTY_SELECTION, sel({ cartCouponId: 1 }))).toBeNull();
  });

  it("포인트가_0인데_포인트_오류면_null이다", () => {
    expect(recoverSelection("POINT_INSUFFICIENT", sel({ cartCouponId: 3 }), EMPTY_SELECTION)).toBeNull();
  });

  it("모르는_코드는_null이다", () => {
    expect(recoverSelection("INTERNAL_ERROR", sel({ points: 100 }), EMPTY_SELECTION)).toBeNull();
  });
});
