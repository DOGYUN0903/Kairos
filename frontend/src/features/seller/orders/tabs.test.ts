import { describe, expect, it } from "vitest";
import { SELLER_ORDER_TABS, parseSellerOrderParams } from "./tabs";

const sp = (s: string) => new URLSearchParams(s);

describe("parseSellerOrderParams", () => {
  it("기본은_전체_0페이지", () => {
    expect(parseSellerOrderParams(sp(""))).toEqual({ page: 0 });
  });

  it("알려진_상태와_페이지를_읽는다", () => {
    expect(parseSellerOrderParams(sp("status=PAID&page=2"))).toEqual({ status: "PAID", page: 2 });
  });

  it("모르는_상태는_전체로_읽는다", () => {
    expect(parseSellerOrderParams(sp("status=PENDING_PAYMENT"))).toEqual({ page: 0 });
    expect(parseSellerOrderParams(sp("status=foo"))).toEqual({ page: 0 });
  });

  it("page가_숫자가_아니면_0이다", () => {
    expect(parseSellerOrderParams(sp("page=abc")).page).toBe(0);
    expect(parseSellerOrderParams(sp("page=-3")).page).toBe(0);
    expect(parseSellerOrderParams(sp("page=1.5")).page).toBe(0);
  });
});

describe("SELLER_ORDER_TABS", () => {
  it("전체와_다섯_상태_순서", () => {
    expect(SELLER_ORDER_TABS.map((t) => t.status)).toEqual([undefined, "PAID", "SHIPPING", "DELIVERED", "CONFIRMED", "CANCELED"]);
    expect(SELLER_ORDER_TABS.map((t) => t.label)).toEqual(["전체", "발송 대기", "배송 중", "배송 완료", "구매 확정", "취소"]);
  });
});
