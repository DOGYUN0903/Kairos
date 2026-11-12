import { describe, expect, it } from "vitest";
import { decidedByLabel, feeBearerNotice, returnReasonLabel, returnStatusLabel } from "@/features/returns/return-text";

describe("return-text", () => {
  it("사유", () => {
    expect(returnReasonLabel("CHANGE_OF_MIND")).toBe("단순 변심");
    expect(returnReasonLabel("DEFECTIVE")).toBe("상품 불량");
    expect(returnReasonLabel("WRONG_DELIVERY")).toBe("오배송");
  });
  it("상태", () => {
    expect(returnStatusLabel("REQUESTED")).toBe("반품 요청됨");
    expect(returnStatusLabel("APPROVED")).toBe("반품 승인 · 수거 대기");
    expect(returnStatusLabel("REFUNDING")).toBe("환불 확인 중");
    expect(returnStatusLabel("COMPLETED")).toBe("반품 완료");
    expect(returnStatusLabel("REJECTED")).toBe("반품 거절");
    expect(returnStatusLabel("WITHDRAWN")).toBe("철회됨");
    expect(returnStatusLabel("CANCELED_BY_ADMIN")).toBe("반품 중단됨");
  });
  it("배송비 부담 안내", () => {
    expect(feeBearerNotice("BUYER")).toBe("반품 배송비는 구매자 부담입니다");
    expect(feeBearerNotice("SELLER")).toBe("판매자 부담이라 차감이 없습니다");
  });
  it("결정 주체", () => {
    expect(decidedByLabel("SELLER")).toBe("판매자");
    expect(decidedByLabel("SYSTEM")).toBe("자동 승인");
    expect(decidedByLabel("ADMIN")).toBe("관리자");
  });
});
