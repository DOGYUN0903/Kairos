import { describe, expect, it } from "vitest";
import { canWithdraw, refundRows, returnEntry } from "@/features/returns/return-actions";

const until = "2026-11-20T23:59:59";
const t = Date.parse(`${until}+09:00`);
const base = { sellerOrderStatus: "DELIVERED", itemStatus: "ORDERED", returnableUntil: until, returnStatus: null };

describe("returnEntry", () => {
  it("기한_1초_전과_정각은_요청_가능", () => {
    expect(returnEntry(base, t - 1000)).toEqual({ kind: "request", until });
    expect(returnEntry(base, t)).toEqual({ kind: "request", until });
  });
  it("기한_1초_뒤는_expired", () => {
    expect(returnEntry(base, t + 1000)).toEqual({ kind: "expired" });
  });
  it("배송_완료가_아닌_판매자_주문은_none", () => {
    for (const s of ["SHIPPING", "CONFIRMED", "PAID"]) {
      expect(returnEntry({ ...base, sellerOrderStatus: s }, t - 1000)).toEqual({ kind: "none" });
    }
  });
  it("반품_진행_중인_항목은_none", () => {
    expect(returnEntry({ ...base, itemStatus: "RETURN_REQUESTED", returnStatus: "REQUESTED" }, t - 1000)).toEqual({ kind: "none" });
    expect(returnEntry({ ...base, returnStatus: "REQUESTED" }, t - 1000)).toEqual({ kind: "none" });
  });
  it("거절·중단은_blocked", () => {
    expect(returnEntry({ ...base, returnStatus: "REJECTED" }, t - 1000)).toEqual({ kind: "blocked", text: "반품이 거절된 상품입니다" });
    expect(returnEntry({ ...base, returnStatus: "CANCELED_BY_ADMIN" }, t - 1000)).toEqual({ kind: "blocked", text: "반품이 중단된 상품입니다" });
  });
  it("철회_뒤에는_다시_요청_가능", () => {
    expect(returnEntry({ ...base, returnStatus: "WITHDRAWN" }, t - 1000)).toEqual({ kind: "request", until });
  });
  it("기한이_없으면_none", () => {
    expect(returnEntry({ ...base, returnableUntil: null }, t)).toEqual({ kind: "none" });
    expect(returnEntry({ ...base, returnableUntil: undefined }, t)).toEqual({ kind: "none" });
  });
});

describe("canWithdraw", () => {
  it("REQUESTED만", () => {
    const all = ["REQUESTED", "APPROVED", "REFUNDING", "COMPLETED", "REJECTED", "WITHDRAWN", "CANCELED_BY_ADMIN"];
    expect(all.filter(canWithdraw)).toEqual(["REQUESTED"]);
    expect(canWithdraw(null)).toBe(false);
    expect(canWithdraw(undefined)).toBe(false);
  });
});

describe("refundRows", () => {
  const z = { paidAmount: 0, returnFee: 0, outboundFeeRecovered: 0, shippingFeeRefund: 0, refundAmount: 0, pointsRefund: 0 };
  it("변심은_4줄", () => {
    const rows = refundRows({ ...z, paidAmount: 20000, returnFee: 3000, outboundFeeRecovered: 3000, refundAmount: 14000 });
    expect(rows.map((r) => r.label)).toEqual(["결제 금액", "반품비", "발송 배송비 회수", "환불 금액"]);
    expect(rows.map((r) => r.amount)).toEqual([20000, 3000, 3000, 14000]);
    expect(rows.map((r) => r.sign)).toEqual(["none", "minus", "minus", "none"]);
  });
  it("불량은_3줄", () => {
    const rows = refundRows({ ...z, paidAmount: 30000, shippingFeeRefund: 3000, refundAmount: 33000 });
    expect(rows.map((r) => r.label)).toEqual(["결제 금액", "배송비 환급", "환불 금액"]);
    expect(rows[1]!.sign).toBe("plus");
  });
  it("포인트_반환이_있으면_줄이_늘어난다", () => {
    const rows = refundRows({ ...z, paidAmount: 20000, refundAmount: 17000, pointsRefund: 3000 });
    expect(rows.map((r) => r.label)).toEqual(["결제 금액", "환불 금액", "포인트 반환"]);
    expect(rows[2]!.unit).toBe("point");
  });
  it("결제_금액과_환불_금액은_0이어도_나온다", () => {
    expect(refundRows(z).map((r) => r.label)).toEqual(["결제 금액", "환불 금액"]);
  });
});
