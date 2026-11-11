import type { components } from "@/api/schema";

export type ProductSort = components["schemas"]["ProductSort"];

export const PRODUCT_SORTS: readonly ProductSort[] = ["LATEST", "PRICE_ASC", "PRICE_DESC", "POPULAR", "LIKES"];

export type ProductListParams = {
  categoryId?: number;
  brandId?: number;
  minPrice?: number;
  maxPrice?: number;
  keyword?: string;
  sort: ProductSort;
  page: number;
  size: number;
};

export const DEFAULT_SORT: ProductSort = "LATEST";
export const DEFAULT_PAGE = 0;
export const DEFAULT_SIZE = 20;
const MAX_SIZE = 100;

function nonNegativeInt(raw: string | null): number | undefined {
  if (raw === null || !/^\d{1,15}$/.test(raw)) return undefined;
  return Number(raw);
}

function positiveInt(raw: string | null): number | undefined {
  const n = nonNegativeInt(raw);
  return n !== undefined && n > 0 ? n : undefined;
}

/** URL 이 단일 진실이다. 잘못된 값은 조용히 기본값(또는 미지정)으로 되돌린다. */
export function parseProductListParams(sp: URLSearchParams): ProductListParams {
  const rawSort = sp.get("sort");
  const sort = PRODUCT_SORTS.find((s) => s === rawSort) ?? DEFAULT_SORT;
  const size = positiveInt(sp.get("size"));
  const keyword = sp.get("keyword")?.trim();

  let minPrice = nonNegativeInt(sp.get("minPrice"));
  let maxPrice = nonNegativeInt(sp.get("maxPrice"));
  if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
    minPrice = undefined;
    maxPrice = undefined;
  }

  const params: ProductListParams = {
    sort,
    page: nonNegativeInt(sp.get("page")) ?? DEFAULT_PAGE,
    size: size !== undefined && size <= MAX_SIZE ? size : DEFAULT_SIZE,
  };
  const categoryId = positiveInt(sp.get("categoryId"));
  const brandId = positiveInt(sp.get("brandId"));
  if (categoryId !== undefined) params.categoryId = categoryId;
  if (brandId !== undefined) params.brandId = brandId;
  if (minPrice !== undefined) params.minPrice = minPrice;
  if (maxPrice !== undefined) params.maxPrice = maxPrice;
  if (keyword) params.keyword = keyword;
  return params;
}

/** 기본값(sort LATEST, page 0, size 20)은 생략한다. 앞의 `?` 는 붙이지 않는다. */
export function toSearchString(p: ProductListParams): string {
  const sp = new URLSearchParams();
  if (p.categoryId !== undefined) sp.set("categoryId", String(p.categoryId));
  if (p.brandId !== undefined) sp.set("brandId", String(p.brandId));
  if (p.minPrice !== undefined) sp.set("minPrice", String(p.minPrice));
  if (p.maxPrice !== undefined) sp.set("maxPrice", String(p.maxPrice));
  if (p.keyword) sp.set("keyword", p.keyword);
  if (p.sort !== DEFAULT_SORT) sp.set("sort", p.sort);
  if (p.page !== DEFAULT_PAGE) sp.set("page", String(p.page));
  if (p.size !== DEFAULT_SIZE) sp.set("size", String(p.size));
  return sp.toString();
}

/** 정렬·검색·필터가 바뀌면 1페이지로 돌아간다. */
export function withFilters(current: ProductListParams, change: Partial<ProductListParams>): ProductListParams {
  return { ...current, ...change, page: change.page ?? DEFAULT_PAGE };
}

/** 정렬·페이지를 뺀 "필터"가 하나라도 걸려 있는가(0건 안내 문구 분기용). */
export function hasActiveFilter(p: ProductListParams, fixedBrandId?: number): boolean {
  return (
    p.categoryId !== undefined ||
    (p.brandId !== undefined && p.brandId !== fixedBrandId) ||
    p.minPrice !== undefined ||
    p.maxPrice !== undefined ||
    p.keyword !== undefined
  );
}
