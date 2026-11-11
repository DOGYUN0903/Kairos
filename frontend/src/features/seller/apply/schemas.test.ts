import { describe, expect, it } from "vitest";
import { applySchema } from "./schemas";

const base = {
  companyName: "판매자 A 주식회사",
  businessNumber: "123-45-67890",
  representativeName: "박서준",
  phone: "02-1234-5678",
  bank: "국민은행",
  accountNumber: "123456-01-234567",
  holder: "판매자 A 주식회사",
};

describe("applySchema", () => {
  it("사업자번호_하이픈은_제거되고_10자리만_통과", () => {
    const r = applySchema.safeParse(base);
    expect(r.success).toBe(true);
    expect(r.data?.businessNumber).toBe("1234567890");
    expect(applySchema.safeParse({ ...base, businessNumber: "1234567890" }).success).toBe(true);
  });

  it("9자리는_거부", () => {
    expect(applySchema.safeParse({ ...base, businessNumber: "123-45-6789" }).success).toBe(false);
  });

  it("숫자가_아닌_문자는_거부", () => {
    expect(applySchema.safeParse({ ...base, businessNumber: "12a-45-67890" }).success).toBe(false);
  });

  it("빈_필드는_거부", () => {
    expect(applySchema.safeParse({ ...base, companyName: " " }).success).toBe(false);
  });
});
