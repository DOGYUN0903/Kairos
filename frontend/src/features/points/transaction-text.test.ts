import { describe, expect, it } from "vitest";
import { signedPoints, transactionLabel, transactionOrderHref } from "@/features/points/transaction-text";

describe("포인트 내역 문구", () => {
  it("유형을_문구로_바꾼다", () => {
    expect(transactionLabel("EARN_PURCHASE")).toBe("구매 확정 적립");
    expect(transactionLabel("EARN_REVIEW")).toBe("후기 작성 적립");
    expect(transactionLabel("USE")).toBe("주문 사용");
    expect(transactionLabel("REFUND")).toBe("주문 취소 환급");
    expect(transactionLabel("EXPIRE")).toBe("기간 만료");
  });

  it("모르는_유형은_그대로_보여준다", () => {
    expect(transactionLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });

  it("ORDER_참조만_주문_링크를_만든다", () => {
    expect(transactionOrderHref({ refType: "ORDER", refId: 42 })).toBe("/my/orders/42");
    expect(transactionOrderHref({ refType: "ORDER_ITEM", refId: 7 })).toBeNull();
    expect(transactionOrderHref({ refType: "PURCHASE_CONFIRM", refId: 7 })).toBeNull();
    expect(transactionOrderHref({ refType: "REVIEW", refId: 7 })).toBeNull();
    expect(transactionOrderHref({ refType: "POINT_LOT", refId: 7 })).toBeNull();
  });

  it("변동량에_부호를_붙인다", () => {
    expect(signedPoints(214)).toBe("+214P");
    expect(signedPoints(-10000)).toBe("−10,000P");
    expect(signedPoints(0)).toBe("0P");
  });
});
