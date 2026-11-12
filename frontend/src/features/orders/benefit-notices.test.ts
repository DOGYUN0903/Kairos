import { describe, expect, it } from "vitest";
import { benefitNotices } from "@/features/orders/benefit-notices";

describe("benefitNotices", () => {
  it("혜택이_없으면_안내가_없다", () => {
    expect(benefitNotices({ pointsAllocated: 0, hasProductCoupon: false, hasCartCoupon: false })).toEqual([]);
  });
  it("포인트가_있으면_함께_돌아온다고_안내한다", () => {
    expect(benefitNotices({ pointsAllocated: 5625, hasProductCoupon: false, hasCartCoupon: false })).toEqual(["사용한 포인트 5,625P가 함께 돌아옵니다"]);
  });
  it("상품_쿠폰은_유효기간이_남아_있으면_돌아온다고_안내한다", () => {
    expect(benefitNotices({ pointsAllocated: 0, hasProductCoupon: true, hasCartCoupon: false })).toEqual(["사용한 상품 쿠폰은 유효기간이 남아 있으면 돌아옵니다"]);
  });
  it("장바구니_쿠폰은_주문_전체_취소가_필요하다고_안내한다", () => {
    expect(benefitNotices({ pointsAllocated: 0, hasProductCoupon: false, hasCartCoupon: true })).toEqual(["장바구니 쿠폰은 주문 전체를 취소해야 돌아옵니다"]);
  });
  it("모두_있으면_세_문구를_순서대로_보인다", () => {
    expect(benefitNotices({ pointsAllocated: 100, hasProductCoupon: true, hasCartCoupon: true })).toHaveLength(3);
  });
});
