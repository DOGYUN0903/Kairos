import { describe, expect, it } from "vitest";
import type { OrderItemResult } from "@/features/orders/api";
import type { SellerOrderDetail } from "@/features/seller/orders/api";
import { canDeliver, canSellerCancelItem, canShip, shipBlockReason } from "./actions";

const item = (status: OrderItemResult["status"]): OrderItemResult => ({ orderItemId: 1, status }) as OrderItemResult;
const so = (status: SellerOrderDetail["status"], items: OrderItemResult["status"][]): SellerOrderDetail =>
  ({ status, items: items.map(item) }) as SellerOrderDetail;

describe("canShip", () => {
  it("발송은_결제_완료이고_취소_확인_중_항목이_없을_때만", () => {
    expect(canShip(so("PAID", ["ORDERED", "CANCELED"]))).toBe(true);
    expect(canShip(so("PAID", ["ORDERED", "CANCEL_REQUESTED"]))).toBe(false);
    expect(canShip(so("SHIPPING", ["ORDERED"]))).toBe(false);
    expect(canShip(so("CANCELED", ["ORDERED"]))).toBe(false);
  });

  it("모든_항목이_취소된_판매자_주문은_발송할_수_없다", () => {
    expect(canShip(so("PAID", ["CANCELED", "CANCELED"]))).toBe(false);
    expect(canShip(so("PAID", []))).toBe(false);
  });
});

describe("canDeliver", () => {
  it("배송_완료는_배송_중일_때만", () => {
    expect(canDeliver(so("SHIPPING", ["ORDERED"]))).toBe(true);
    expect(canDeliver(so("PAID", ["ORDERED"]))).toBe(false);
    expect(canDeliver(so("DELIVERED", ["ORDERED"]))).toBe(false);
  });
});

describe("canSellerCancelItem", () => {
  it("항목_취소는_배송_전_주문_완료_항목만", () => {
    expect(canSellerCancelItem(so("PAID", ["ORDERED"]), item("ORDERED"))).toBe(true);
    expect(canSellerCancelItem(so("PAID", ["ORDERED"]), item("CANCEL_REQUESTED"))).toBe(false);
    expect(canSellerCancelItem(so("PAID", ["ORDERED"]), item("CANCELED"))).toBe(false);
    expect(canSellerCancelItem(so("SHIPPING", ["ORDERED"]), item("ORDERED"))).toBe(false);
  });
});

describe("shipBlockReason", () => {
  it("발송_불가_이유를_고른다", () => {
    expect(shipBlockReason(so("PAID", ["ORDERED"]))).toBeNull();
    expect(shipBlockReason(so("PAID", ["CANCELED", "CANCELED"]))).toBe("발송할 상품이 없습니다");
    expect(shipBlockReason(so("PAID", ["ORDERED", "CANCEL_REQUESTED"]))).toBe("취소 확인 중인 상품이 있습니다");
    expect(shipBlockReason(so("SHIPPING", ["ORDERED"]))).toBeNull();
  });
});
