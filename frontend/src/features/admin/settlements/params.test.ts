import { describe, expect, it } from "vitest";
import { parseAdminSettlementParams, toAdminSettlementSearch } from "@/features/admin/settlements/params";

const sp = (s: string) => new URLSearchParams(s);

describe("관리자 정산 목록 URL 쿼리", () => {
  it("정상_값을_읽는다", () => expect(parseAdminSettlementParams(sp("sellerId=12&status=PAID&page=2"))).toEqual({ sellerId: 12, status: "PAID", page: 2 }));
  it("잘못된_값은_무시한다", () => expect(parseAdminSettlementParams(sp("sellerId=abc&status=X&page=-1"))).toEqual({ page: 0 }));
  it("기본값은_싣지_않는다", () => {
    expect(toAdminSettlementSearch({ page: 0 })).toBe("");
    expect(toAdminSettlementSearch({ sellerId: 3, status: "CONFIRMED", page: 1 })).toBe("sellerId=3&status=CONFIRMED&page=1");
  });
});
