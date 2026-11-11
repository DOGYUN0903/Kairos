import { describe, expect, it } from "vitest";
import { hasActiveFilter, parseProductListParams, toSearchString, withFilters } from "./search-params";

const parse = (q: string) => parseProductListParams(new URLSearchParams(q));

describe("parseProductListParams", () => {
  it("기본값은_LATEST_0_20", () => {
    expect(parse("")).toEqual({ sort: "LATEST", page: 0, size: 20 });
  });

  it("잘못된_sort는_LATEST로", () => {
    expect(parse("sort=CHEAPEST").sort).toBe("LATEST");
    expect(parse("sort=likes").sort).toBe("LATEST");
    expect(parse("sort=LIKES").sort).toBe("LIKES");
  });

  it("잘못된_숫자는_기본값이거나_무시된다", () => {
    expect(parse("page=-1&size=0&categoryId=abc&brandId=0&minPrice=1.5")).toEqual({ sort: "LATEST", page: 0, size: 20 });
    expect(parse("size=101").size).toBe(20);
    expect(parse("page=3&size=40")).toMatchObject({ page: 3, size: 40 });
  });

  it("minPrice가_maxPrice보다_크면_둘_다_버린다", () => {
    const p = parse("minPrice=50000&maxPrice=10000");
    expect(p.minPrice).toBeUndefined();
    expect(p.maxPrice).toBeUndefined();
    expect(parse("minPrice=10000&maxPrice=10000")).toMatchObject({ minPrice: 10000, maxPrice: 10000 });
  });

  it("keyword는_앞뒤_공백을_자르고_빈_값이면_버린다", () => {
    expect(parse("keyword=%20%20티셔츠%20").keyword).toBe("티셔츠");
    expect(parse("keyword=%20%20").keyword).toBeUndefined();
  });
});

describe("toSearchString", () => {
  it("toSearchString은_기본값을_생략한다", () => {
    expect(toSearchString({ sort: "LATEST", page: 0, size: 20 })).toBe("");
    expect(toSearchString({ categoryId: 3, sort: "LIKES", page: 2, size: 20 })).toBe("categoryId=3&sort=LIKES&page=2");
  });

  it("한글_키워드는_왕복해도_같다", () => {
    const original = parse("keyword=오버핏%20티&minPrice=1000&maxPrice=30000&sort=PRICE_ASC&page=1");
    expect(parse(toSearchString(original))).toEqual(original);
  });
});

describe("withFilters / hasActiveFilter", () => {
  it("필터를_바꾸면_페이지가_0으로_돌아간다", () => {
    const next = withFilters({ sort: "LATEST", page: 4, size: 20 }, { sort: "LIKES" });
    expect(next).toEqual({ sort: "LIKES", page: 0, size: 20 });
  });

  it("정렬과_페이지만_있으면_필터가_없다고_본다", () => {
    expect(hasActiveFilter({ sort: "LIKES", page: 2, size: 20 })).toBe(false);
    expect(hasActiveFilter({ keyword: "a", sort: "LATEST", page: 0, size: 20 })).toBe(true);
    expect(hasActiveFilter({ brandId: 7, sort: "LATEST", page: 0, size: 20 }, 7)).toBe(false);
  });
});
