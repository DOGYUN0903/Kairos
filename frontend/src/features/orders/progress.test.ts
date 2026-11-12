import { describe, expect, it } from "vitest";
import { orderProgressLabel } from "@/features/orders/progress";

describe("orderProgressLabel", () => {
  it("결제 대기 주문은 결제 대기", () => {
    expect(orderProgressLabel("PENDING_PAYMENT", ["PENDING_PAYMENT"])).toBe("결제 대기");
  });
  it("취소 주문은 취소", () => {
    expect(orderProgressLabel("CANCELED", ["CANCELED"])).toBe("취소");
  });
  it("구매 확정 주문은 구매 확정", () => {
    expect(orderProgressLabel("COMPLETED", ["CONFIRMED"])).toBe("구매 확정");
  });
  it("판매자 주문이 한 종류면 그 문구", () => {
    expect(orderProgressLabel("PAID", ["PAID"])).toBe("배송 준비 중");
    expect(orderProgressLabel("PAID", ["SHIPPING", "SHIPPING"])).toBe("배송 중");
    expect(orderProgressLabel("PAID", ["DELIVERED"])).toBe("배송 완료");
    expect(orderProgressLabel("PAID", ["CONFIRMED"])).toBe("구매 확정");
  });
  it("여러 종류면 배송 중, 배송 준비, 배송 완료, 구매 확정 순으로 개수와 함께", () => {
    expect(orderProgressLabel("PAID", ["SHIPPING", "PAID"])).toBe("배송 중 1 · 배송 준비 1");
    expect(orderProgressLabel("PAID", ["DELIVERED", "PAID", "SHIPPING", "SHIPPING", "CONFIRMED"])).toBe(
      "배송 중 2 · 배송 준비 1 · 배송 완료 1 · 구매 확정 1",
    );
  });
  it("취소된 판매자 주문은 세지 않는다", () => {
    expect(orderProgressLabel("PAID", ["CANCELED", "DELIVERED"])).toBe("배송 완료");
    expect(orderProgressLabel("PAID", ["CANCELED", "SHIPPING", "PAID"])).toBe("배송 중 1 · 배송 준비 1");
  });
  it("남은 판매자 주문이 없으면 결제 완료", () => {
    expect(orderProgressLabel("PAID", ["CANCELED", "CANCELED"])).toBe("결제 완료");
    expect(orderProgressLabel("PAID", [])).toBe("결제 완료");
  });
});
