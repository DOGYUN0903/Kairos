import { describe, expect, it } from "vitest";
import {
  clearCoupons,
  couponAppliedElsewhere,
  EMPTY_SELECTION,
  isSameSelection,
  selectCartCoupon,
  selectProductCoupon,
  setPoints,
  toBenefitBody,
  type BenefitSelection,
} from "@/features/checkout/benefit-selection";

const sel = (over: Partial<BenefitSelection> = {}): BenefitSelection => ({ ...EMPTY_SELECTION, ...over });

describe("benefit-selection", () => {
  it("다른_상품에_고른_쿠폰을_고르면_이쪽으로_옮긴다", () => {
    expect(selectProductCoupon(sel({ productCoupons: { 1: 10 } }), 2, 10).productCoupons).toEqual({ 2: 10 });
  });

  it("null을_고르면_그_상품의_쿠폰이_빠진다", () => {
    expect(selectProductCoupon(sel({ productCoupons: { 1: 10, 2: 11 } }), 1, null).productCoupons).toEqual({ 2: 11 });
  });

  it("같은_상품에_다른_쿠폰을_고르면_바꾼다", () => {
    expect(selectProductCoupon(sel({ productCoupons: { 1: 10 } }), 1, 12).productCoupons).toEqual({ 1: 12 });
  });

  it("장바구니_쿠폰을_고르고_해제한다", () => {
    const picked = selectCartCoupon(EMPTY_SELECTION, 5);
    expect(picked.cartCouponId).toBe(5);
    expect(selectCartCoupon(picked, null).cartCouponId).toBeNull();
  });

  it("clearCoupons는_포인트를_남긴다", () => {
    expect(clearCoupons(sel({ productCoupons: { 1: 10 }, cartCouponId: 3, points: 1000 }))).toEqual({ productCoupons: {}, cartCouponId: null, points: 1000 });
  });

  it("setPoints는_포인트만_바꾼다", () => {
    expect(setPoints(sel({ cartCouponId: 3 }), 2000)).toEqual({ productCoupons: {}, cartCouponId: 3, points: 2000 });
  });

  it("toBenefitBody는_skuId_오름차순이다", () => {
    expect(toBenefitBody(sel({ productCoupons: { 5: 1, 2: 3 }, cartCouponId: 9, points: 500 }))).toEqual({
      productCoupons: [
        { skuId: 2, couponId: 3 },
        { skuId: 5, couponId: 1 },
      ],
      cartCouponId: 9,
      pointsToUse: 500,
    });
  });

  it("입력_객체를_바꾸지_않는다", () => {
    const input = sel({ productCoupons: { 1: 10 }, cartCouponId: 2, points: 100 });
    const frozen = JSON.stringify(input);
    selectProductCoupon(input, 2, 10);
    selectCartCoupon(input, null);
    setPoints(input, 0);
    clearCoupons(input);
    toBenefitBody(input);
    expect(JSON.stringify(input)).toBe(frozen);
  });

  it("isSameSelection은_키_순서와_무관하다", () => {
    expect(isSameSelection(sel({ productCoupons: { 1: 10, 2: 11 } }), sel({ productCoupons: { 2: 11, 1: 10 } }))).toBe(true);
    expect(isSameSelection(sel({ productCoupons: { 1: 10 } }), sel({ productCoupons: { 1: 11 } }))).toBe(false);
    expect(isSameSelection(sel({ points: 10 }), sel({ points: 20 }))).toBe(false);
    expect(isSameSelection(sel({ cartCouponId: 1 }), sel({ cartCouponId: null }))).toBe(false);
  });

  it("couponAppliedElsewhere는_다른_상품에_적용된_쿠폰만_true다", () => {
    const s = sel({ productCoupons: { 1: 10 } });
    expect(couponAppliedElsewhere(s, 2, 10)).toBe(true);
    expect(couponAppliedElsewhere(s, 1, 10)).toBe(false);
    expect(couponAppliedElsewhere(s, 2, 11)).toBe(false);
  });
});
