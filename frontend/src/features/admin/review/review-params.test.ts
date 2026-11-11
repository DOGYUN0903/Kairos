import { describe, expect, it } from "vitest";
import { parseReviewParams, toReviewSearch } from "./review-params";

const sp = (s: string) => new URLSearchParams(s);

describe("parseReviewParams", () => {
  it("기본은_sellers_OLDEST", () => {
    expect(parseReviewParams(sp(""))).toEqual({ tab: "sellers", id: null, page: 0, sort: "OLDEST" });
  });

  it("잘못된_tab은_sellers", () => {
    expect(parseReviewParams(sp("tab=orders")).tab).toBe("sellers");
  });

  it("id_page_sort를_읽고_잘못된_값은_기본으로_되돌린다", () => {
    expect(parseReviewParams(sp("tab=change-requests&id=7&page=2&sort=LATEST"))).toEqual({
      tab: "change-requests",
      id: 7,
      page: 2,
      sort: "LATEST",
    });
    expect(parseReviewParams(sp("id=0&page=-1&sort=RANDOM"))).toEqual({ tab: "sellers", id: null, page: 0, sort: "OLDEST" });
    expect(parseReviewParams(sp("id=1e3")).id).toBeNull();
  });
});

describe("toReviewSearch", () => {
  it("기본값은_싣지_않는다", () => {
    expect(toReviewSearch({ tab: "sellers", id: null, page: 0, sort: "OLDEST" })).toBe("");
    expect(toReviewSearch({ tab: "products", id: 3, page: 1, sort: "LATEST" })).toBe("tab=products&id=3&page=1&sort=LATEST");
  });
});
