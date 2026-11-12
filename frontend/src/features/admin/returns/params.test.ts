import { describe, expect, it } from "vitest";
import { adminReturnActions, parseAdminReturnParams, toAdminReturnSearch } from "./params";

const sp = (q: string) => new URLSearchParams(q);

describe("parseAdminReturnParams", () => {
  it("값이_없으면_첫_페이지만_돌려준다", () => {
    expect(parseAdminReturnParams(sp(""))).toEqual({ page: 0 });
  });

  it("올바른_필터를_읽는다", () => {
    expect(parseAdminReturnParams(sp("status=REJECTED&sellerId=12&memberId=7&page=2"))).toEqual({ status: "REJECTED", sellerId: 12, memberId: 7, page: 2 });
  });

  it("잘못된_sellerId와_memberId는_버린다", () => {
    for (const bad of ["abc", "0", "-3", "1.5", "007", "", "99999999999999999999"]) {
      expect(parseAdminReturnParams(sp(`sellerId=${bad}&memberId=${bad}`))).toEqual({ page: 0 });
    }
  });

  it("모르는_status는_버린다", () => {
    expect(parseAdminReturnParams(sp("status=NOPE"))).toEqual({ page: 0 });
    expect(parseAdminReturnParams(sp("status=requested"))).toEqual({ page: 0 });
  });

  it("철회와_중단_상태도_필터로_쓸_수_있다", () => {
    expect(parseAdminReturnParams(sp("status=CANCELED_BY_ADMIN")).status).toBe("CANCELED_BY_ADMIN");
    expect(parseAdminReturnParams(sp("status=WITHDRAWN")).status).toBe("WITHDRAWN");
  });
});

describe("toAdminReturnSearch", () => {
  it("기본값은_싣지_않는다", () => {
    expect(toAdminReturnSearch({ page: 0 })).toBe("");
    expect(toAdminReturnSearch({ status: "APPROVED", sellerId: 3, memberId: 4, page: 1 })).toBe("status=APPROVED&sellerId=3&memberId=4&page=1");
  });
});

describe("adminReturnActions", () => {
  it("상태별_작업", () => {
    expect(adminReturnActions("REQUESTED")).toEqual(["approve"]);
    expect(adminReturnActions("REJECTED")).toEqual(["approve"]);
    expect(adminReturnActions("APPROVED")).toEqual(["receive", "cancel"]);
    for (const s of ["WITHDRAWN", "REFUNDING", "COMPLETED", "CANCELED_BY_ADMIN"]) expect(adminReturnActions(s)).toEqual([]);
  });
});
