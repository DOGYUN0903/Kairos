import { describe, expect, it } from "vitest";
import { noRefundNotice, decidedByLabel, feeBearerLabel, RETURN_FILTER_LABEL, feeBearerNotice, needsReturnPolling, refundSummaryText, returnReasonLabel, returnStatusLabel } from "@/features/returns/return-text";

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

  it("환불_요약은_완료일_때만_금액", () => {
    expect(refundSummaryText({ status: "COMPLETED", refundAmount: 25000 })).toBe("25,000원");
    for (const status of ["REQUESTED", "APPROVED", "REFUNDING"] as const) {
      expect(refundSummaryText({ status, refundAmount: 25000 })).toBe("수거 확인 뒤 확정");
    }
  });
  it("환불_없이_끝난_반품은_환불_없음", () => {
    for (const status of ["REJECTED", "WITHDRAWN", "CANCELED_BY_ADMIN"] as const) {
      expect(refundSummaryText({ status, refundAmount: 0 })).toBe("환불 없음");
      expect(refundSummaryText({ status, refundAmount: 25000 })).toBe("환불 없음");
    }
  });
  it("환불_없이_끝난_반품의_상세_안내", () => {
    expect(noRefundNotice("REJECTED")).toBe("거절된 반품이라 환불이 없습니다.");
    expect(noRefundNotice("WITHDRAWN")).toBe("철회된 반품이라 환불이 없습니다.");
    expect(noRefundNotice("CANCELED_BY_ADMIN")).toBe("중단된 반품이라 환불이 없습니다.");
    for (const s of ["REQUESTED", "APPROVED", "REFUNDING", "COMPLETED"]) expect(noRefundNotice(s)).toBeUndefined();
  });
  it("재조회는_환불_확인_중일_때만", () => {
    expect(needsReturnPolling("REFUNDING")).toBe(true);
    for (const s of ["REQUESTED", "APPROVED", "COMPLETED", "REJECTED", "WITHDRAWN", "CANCELED_BY_ADMIN"]) expect(needsReturnPolling(s)).toBe(false);
  });
});

describe("feeBearerLabel·RETURN_FILTER_LABEL", () => {
  it("부담_주체_이름", () => {
    expect(feeBearerLabel("BUYER")).toBe("구매자");
    expect(feeBearerLabel("SELLER")).toBe("판매자");
  });
  it("필터_이름은_모든_상태를_덮는다", () => {
    expect(Object.keys(RETURN_FILTER_LABEL).sort()).toEqual(["APPROVED", "CANCELED_BY_ADMIN", "COMPLETED", "REFUNDING", "REJECTED", "REQUESTED", "WITHDRAWN"]);
  });
});
