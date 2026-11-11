export const REVIEW_TABS = ["sellers", "brands", "products", "change-requests"] as const;
export type ReviewTab = (typeof REVIEW_TABS)[number];
export type ReviewSort = "LATEST" | "OLDEST";

export type ReviewParams = { tab: ReviewTab; id: number | null; page: number; sort: ReviewSort };

export const DEFAULT_TAB: ReviewTab = "sellers";
export const DEFAULT_SORT: ReviewSort = "OLDEST";
export const REVIEW_PAGE_SIZE = 20;

/** 큐 탭마다 "검수 대기"를 뜻하는 상태 값. */
export const PENDING_STATUS = {
  sellers: "PENDING",
  brands: "PENDING",
  products: "PENDING_REVIEW",
  "change-requests": "PENDING",
} as const satisfies Record<ReviewTab, string>;

export const TAB_LABEL: Record<ReviewTab, string> = {
  sellers: "판매자",
  brands: "브랜드",
  products: "상품",
  "change-requests": "변경 요청",
};

function positiveInt(raw: string | null): number | null {
  if (raw === null || !/^[1-9]\d{0,14}$/.test(raw)) return null;
  return Number(raw);
}

/** URL 이 진실이다. 잘못된 값은 조용히 기본값으로 되돌린다. */
export function parseReviewParams(sp: { get(name: string): string | null }): ReviewParams {
  const rawTab = sp.get("tab");
  const tab = REVIEW_TABS.find((t) => t === rawTab) ?? DEFAULT_TAB;
  const rawPage = sp.get("page");
  const page = rawPage !== null && /^\d{1,9}$/.test(rawPage) ? Number(rawPage) : 0;
  return { tab, id: positiveInt(sp.get("id")), page, sort: sp.get("sort") === "LATEST" ? "LATEST" : DEFAULT_SORT };
}

/** 기본값은 URL 에 싣지 않는다. 앞의 `?` 는 붙이지 않는다. */
export function toReviewSearch(p: ReviewParams): string {
  const q = new URLSearchParams();
  if (p.tab !== DEFAULT_TAB) q.set("tab", p.tab);
  if (p.id !== null) q.set("id", String(p.id));
  if (p.page > 0) q.set("page", String(p.page));
  if (p.sort !== DEFAULT_SORT) q.set("sort", p.sort);
  return q.toString();
}
