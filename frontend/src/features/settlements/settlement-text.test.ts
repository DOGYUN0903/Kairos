import { describe, expect, it } from "vitest";
import { adjustmentTypeLabel, isCarriedOver, settlementRows, settlementStatusLabel } from "@/features/settlements/settlement-text";

const d = (o: Partial<Parameters<typeof settlementRows>[0]> = {}) => ({
  salesAmount: 0, sellerCouponDiscount: 0, commissionAmount: 0, shippingFeeOwed: 0, returnFeeBorne: 0, adjustmentAmount: 0, totalAmount: 0, payableAmount: 0, ...o,
});

describe("settlementRows", () => {
  it("여덟_줄_순서와_문자열", () => {
    const rows = settlementRows(d({ salesAmount: 95000, sellerCouponDiscount: 5000, commissionAmount: 9000, shippingFeeOwed: 6000, returnFeeBorne: 6000, adjustmentAmount: 0, totalAmount: 81000, payableAmount: 81000 }));
    expect(rows.map((r) => r.label)).toEqual(["상품 매출", "판매자 쿠폰 할인", "수수료", "배송비", "판매자 부담 반품비", "조정", "합계", "지급액"]);
    expect(rows.map((r) => r.text)).toEqual(["95,000원", "−5,000원", "−9,000원", "+6,000원", "−6,000원", "0원", "81,000원", "81,000원"]);
  });
  it("전부_0이면_여덟_줄_모두_0원", () => {
    const rows = settlementRows(d());
    expect(rows).toHaveLength(8);
    expect(rows.every((r) => r.text === "0원")).toBe(true);
  });
  it("음수_합계는_이월", () => {
    const x = d({ totalAmount: -3000, payableAmount: 0 });
    const rows = settlementRows(x);
    expect(rows[6]!.text).toBe("−3,000원");
    expect(rows[7]!.text).toBe("0원");
    expect(isCarriedOver(x)).toBe(true);
    expect(isCarriedOver(d({ totalAmount: 0 }))).toBe(false);
  });
  it("조정_음수", () => {
    expect(settlementRows(d({ adjustmentAmount: -10000 }))[5]!.text).toBe("−10,000원");
  });
});

describe("settlementRows sign", () => {
  it("줄마다_기호가_붙는다", () => {
    expect(settlementRows(d()).map((r) => r.sign)).toEqual(["", "−", "−", "+", "−", "±", "=", ""]);
  });
});

describe("라벨", () => {
  it("정산 상태", () => {
    expect(settlementStatusLabel("GENERATED")).toBe("정산 생성");
    expect(settlementStatusLabel("CONFIRMED")).toBe("확정");
    expect(settlementStatusLabel("PAID")).toBe("지급 완료");
  });
  it("조정 유형", () => {
    expect(adjustmentTypeLabel("MANUAL")).toBe("수동 조정");
    expect(adjustmentTypeLabel("CARRY_OVER")).toBe("이월");
  });
});
