import { describe, expect, it } from "vitest";
import { policyFormSchema, toPolicyRequest, type PolicyFormInput } from "@/features/coupon-policies/schema";

const base: PolicyFormInput = {
  name: "연말 쿠폰",
  couponType: "PRODUCT",
  targetType: "SELLER",
  targetProductId: "",
  discountType: "FIXED",
  discountValue: "1000",
  percent: "",
  maxDiscount: "",
  minOrderAmount: "",
  totalQuantity: "",
  validityDays: "",
  validFrom: "2026-12-01T00:00",
  validUntil: "2026-12-31T23:59",
};

function run(scope: "seller" | "admin", patch: Partial<PolicyFormInput> = {}) {
  const r = policyFormSchema(scope).safeParse({ ...base, ...patch });
  return r.success ? { ok: true as const, body: toPolicyRequest(r.data, scope) } : { ok: false as const, fields: r.error.issues.map((i) => String(i.path[0])) };
}

describe("정책 양식 스키마 - 공통", () => {
  it("정액 정책은 통과하고 최대 할인이 요청에 없다", () => {
    const r = run("seller");
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.body).not.toHaveProperty("maxDiscount");
      expect(r.body.discountValue).toBe(1000);
      expect(r.body.validFrom).toBe("2026-12-01T00:00:00");
    }
  });
  it("정액인데 정률 입력과 최대 할인이 남아 있어도 보내지 않는다", () => {
    const r = run("seller", { percent: "10", maxDiscount: "5000" });
    expect(r.ok && r.body).not.toHaveProperty("maxDiscount");
  });
  it("정률인데 최대 할인이 없으면 실패한다", () => {
    const r = run("seller", { discountType: "RATE", percent: "10" });
    expect(r).toEqual({ ok: false, fields: ["maxDiscount"] });
  });
  it("정률 10%는 1000bp로 보낸다", () => {
    const r = run("seller", { discountType: "RATE", percent: "10", maxDiscount: "5000" });
    expect(r.ok && r.body.discountValue).toBe(1000);
    expect(r.ok && r.body.maxDiscount).toBe(5000);
  });
  it.each(["0", "101", "10.5", "", "-3"])("정률 %s%%는 실패한다", (percent) => {
    expect(run("seller", { discountType: "RATE", percent, maxDiscount: "5000" }).ok).toBe(false);
  });
  it("정액 0원·소수는 실패한다", () => {
    expect(run("seller", { discountValue: "0" }).ok).toBe(false);
    expect(run("seller", { discountValue: "10.5" }).ok).toBe(false);
  });
  it("비운 선택 값은 null 이다", () => {
    const r = run("seller");
    expect(r.ok && r.body).toMatchObject({ minOrderAmount: null, totalQuantity: null, validityDays: null, targetProductId: null });
  });
  it("채운 선택 값은 양의 정수로 보낸다", () => {
    const r = run("seller", { minOrderAmount: "10,000", totalQuantity: "300", validityDays: "14" });
    expect(r.ok && r.body).toMatchObject({ minOrderAmount: 10000, totalQuantity: 300, validityDays: 14 });
  });
  it("선택 값이 0이거나 소수면 실패한다", () => {
    expect(run("seller", { totalQuantity: "0" }).ok).toBe(false);
    expect(run("seller", { validityDays: "1.5" }).ok).toBe(false);
    expect(run("seller", { minOrderAmount: "-1" }).ok).toBe(false);
  });
  it("종료가 시작보다 앞서거나 같으면 실패한다", () => {
    expect(run("seller", { validUntil: "2026-12-01T00:00" })).toEqual({ ok: false, fields: ["validUntil"] });
    expect(run("seller", { validUntil: "2026-11-30T00:00" }).ok).toBe(false);
  });
  it("일시 형식이 아니면 실패한다", () => {
    expect(run("seller", { validFrom: "" }).ok).toBe(false);
  });
  it("이름은 앞뒤 공백을 자르고 1~50자", () => {
    expect(run("seller", { name: "   " }).ok).toBe(false);
    expect(run("seller", { name: "가".repeat(50) }).ok).toBe(true);
    expect(run("seller", { name: "가".repeat(51) }).ok).toBe(false);
    const r = run("seller", { name: "  쿠폰  " });
    expect(r.ok && r.body.name).toBe("쿠폰");
  });
});

describe("정책 양식 스키마 - 판매자", () => {
  it("상품 하나인데 상품이 없으면 실패한다", () => {
    expect(run("seller", { targetType: "PRODUCT" })).toEqual({ ok: false, fields: ["targetProductId"] });
  });
  it("상품 하나는 상품 ID 를 보낸다", () => {
    const r = run("seller", { targetType: "PRODUCT", targetProductId: "1024" });
    expect(r.ok && r.body).toMatchObject({ targetType: "PRODUCT", targetProductId: 1024 });
  });
  it("내 상품 전체면 남아 있는 상품 ID 를 버린다", () => {
    const r = run("seller", { targetType: "SELLER", targetProductId: "1024" });
    expect(r.ok && r.body.targetProductId).toBeNull();
  });
  it("쿠폰 종류는 항상 PRODUCT 다", () => {
    const r = run("seller", { couponType: "CART" });
    expect(r.ok && r.body.couponType).toBe("PRODUCT");
  });
  it("판매자는 전 상품·주문 전체 대상을 고를 수 없다", () => {
    expect(run("seller", { targetType: "ALL_PRODUCTS" }).ok).toBe(false);
    expect(run("seller", { targetType: "NONE" }).ok).toBe(false);
  });
});

describe("정책 양식 스키마 - 관리자", () => {
  it("장바구니 쿠폰은 대상 NONE, 상품 없음", () => {
    const r = run("admin", { couponType: "CART", targetType: "PRODUCT", targetProductId: "5" });
    expect(r.ok && r.body).toMatchObject({ couponType: "CART", targetType: "NONE", targetProductId: null });
  });
  it("상품 쿠폰 전 상품", () => {
    const r = run("admin", { couponType: "PRODUCT", targetType: "ALL_PRODUCTS" });
    expect(r.ok && r.body).toMatchObject({ couponType: "PRODUCT", targetType: "ALL_PRODUCTS", targetProductId: null });
  });
  it("상품 쿠폰 상품 하나는 상품 ID 가 필수다", () => {
    expect(run("admin", { couponType: "PRODUCT", targetType: "PRODUCT" })).toEqual({ ok: false, fields: ["targetProductId"] });
    expect(run("admin", { couponType: "PRODUCT", targetType: "PRODUCT", targetProductId: "abc" }).ok).toBe(false);
    const r = run("admin", { couponType: "PRODUCT", targetType: "PRODUCT", targetProductId: "77" });
    expect(r.ok && r.body.targetProductId).toBe(77);
  });
  it("상품 쿠폰에 내 상품 전체(SELLER)·NONE 은 고를 수 없다", () => {
    expect(run("admin", { couponType: "PRODUCT", targetType: "SELLER" }).ok).toBe(false);
    expect(run("admin", { couponType: "PRODUCT", targetType: "NONE" }).ok).toBe(false);
  });
});
