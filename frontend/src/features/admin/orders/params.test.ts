import { describe, expect, it } from "vitest";
import { parseAdminOrderParams } from "./params";

const sp = (s: string) => new URLSearchParams(s);

describe("parseAdminOrderParams", () => {
  it("기본은_필터_없이_0페이지", () => {
    expect(parseAdminOrderParams(sp(""))).toEqual({ page: 0 });
  });

  it("상태_회원_주문번호_페이지를_읽는다", () => {
    expect(parseAdminOrderParams(sp("status=PAID&memberId=7&orderNo=K123&page=2"))).toEqual({ status: "PAID", memberId: 7, orderNo: "K123", page: 2 });
  });

  it("memberId가_숫자가_아니면_무시한다", () => {
    expect(parseAdminOrderParams(sp("memberId=abc")).memberId).toBeUndefined();
    expect(parseAdminOrderParams(sp("memberId=-1")).memberId).toBeUndefined();
    expect(parseAdminOrderParams(sp("memberId=0")).memberId).toBeUndefined();
    expect(parseAdminOrderParams(sp("memberId=1.5")).memberId).toBeUndefined();
  });

  it("orderNo는_앞뒤_공백을_없애고_빈_값이면_무시한다", () => {
    expect(parseAdminOrderParams(sp("orderNo=%20K1%20")).orderNo).toBe("K1");
    expect(parseAdminOrderParams(sp("orderNo=%20%20")).orderNo).toBeUndefined();
    expect(parseAdminOrderParams(sp("orderNo=")).orderNo).toBeUndefined();
  });

  it("모르는_status는_무시한다", () => {
    expect(parseAdminOrderParams(sp("status=SHIPPING")).status).toBeUndefined();
    expect(parseAdminOrderParams(sp("status=foo")).status).toBeUndefined();
  });

  it("page가_숫자가_아니면_0이다", () => {
    expect(parseAdminOrderParams(sp("page=-1")).page).toBe(0);
    expect(parseAdminOrderParams(sp("page=x")).page).toBe(0);
  });
});
