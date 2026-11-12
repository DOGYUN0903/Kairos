import { describe, expect, it } from "vitest";
import { adminTitleOf, sellerTitleOf } from "@/components/layout/console-titles";

describe("sellerTitleOf", () => {
  it.each([
    ["/seller", "대시보드"],
    ["/seller/apply", "입점 신청"],
    ["/seller/products/12", "상품 관리"],
    ["/seller/orders/3", "주문 관리"],
    ["/seller/brands", "브랜드"],
    ["/seller/coupons", "쿠폰 관리"],
    ["/seller/coupons/new", "쿠폰 관리"],
    ["/seller/reviews", "후기 관리"],
    ["/seller/returns", "반품 관리"],
    ["/seller/returns/5", "반품 관리"],
    ["/seller/settlements", "정산"],
    ["/seller/settlements/9", "정산"],
  ])("%s -> %s", (path, title) => expect(sellerTitleOf(path)).toBe(title));
});

describe("adminTitleOf", () => {
  it("새 화면은 자기 제목을 갖는다", () => {
    expect(adminTitleOf("/admin/coupons")).toBe("쿠폰 관리");
    expect(adminTitleOf("/admin/members/points")).toBe("회원 포인트");
    expect(adminTitleOf("/admin/returns")).toBe("반품");
    expect(adminTitleOf("/admin/returns/4")).toBe("반품");
    expect(adminTitleOf("/admin/settlements")).toBe("정산");
    expect(adminTitleOf("/admin/settlements/4")).toBe("정산");
    expect(adminTitleOf("/admin/settlements/adjustments")).toBe("정산 조정");
  });
  it("기존 화면은 기본 제목(undefined)", () => {
    expect(adminTitleOf("/admin/orders")).toBeUndefined();
  });
});
