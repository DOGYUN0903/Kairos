import { describe, expect, it } from "vitest";
import { shippingAddressSchema } from "@/features/checkout/schemas";

const ok = { receiverName: "김민지", phone: "010-1234-5678", zipCode: "04524", address1: "서울특별시 중구 세종대로 110", address2: "302호", memo: "" };

describe("shippingAddressSchema", () => {
  it("정상_값은_통과하고_빈_메모는_undefined가_된다", () => {
    const r = shippingAddressSchema.safeParse(ok);
    expect(r.success).toBe(true);
    expect(r.success && r.data.memo).toBeUndefined();
  });
  it("메모가_있으면_그대로_둔다", () => {
    const r = shippingAddressSchema.safeParse({ ...ok, memo: "문 앞" });
    expect(r.success && r.data.memo).toBe("문 앞");
  });
  it.each([
    ["receiverName", 50],
    ["address1", 200],
    ["address2", 200],
    ["memo", 200],
  ] as const)("%s는_%i자까지_허용하고_넘으면_실패한다", (key, max) => {
    expect(shippingAddressSchema.safeParse({ ...ok, [key]: "가".repeat(max) }).success).toBe(true);
    expect(shippingAddressSchema.safeParse({ ...ok, [key]: "가".repeat(max + 1) }).success).toBe(false);
  });
  it("우편번호는_10자까지, 연락처는_20자까지이다", () => {
    expect(shippingAddressSchema.safeParse({ ...ok, zipCode: "1".repeat(10) }).success).toBe(true);
    expect(shippingAddressSchema.safeParse({ ...ok, zipCode: "1".repeat(11) }).success).toBe(false);
    expect(shippingAddressSchema.safeParse({ ...ok, phone: "1".repeat(20) }).success).toBe(true);
    expect(shippingAddressSchema.safeParse({ ...ok, phone: "1".repeat(21) }).success).toBe(false);
  });
  it("연락처에_문자가_있으면_실패한다", () => {
    expect(shippingAddressSchema.safeParse({ ...ok, phone: "010-abcd-5678" }).success).toBe(false);
    expect(shippingAddressSchema.safeParse({ ...ok, phone: "010 1234 5678" }).success).toBe(false);
  });
  it.each(["receiverName", "phone", "zipCode", "address1", "address2"] as const)("%s가_비면_실패한다", (key) => {
    expect(shippingAddressSchema.safeParse({ ...ok, [key]: "" }).success).toBe(false);
  });
});
