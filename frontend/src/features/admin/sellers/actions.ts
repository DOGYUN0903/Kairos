import type { components } from "@/api/schema";

type SellerStatus = components["schemas"]["SellerStatus"];

/** 백엔드 규칙: 정지는 APPROVED 만, 해제는 SUSPENDED 만. 심사 중·반려는 심사 큐에서 처리한다. */
export function sellerActions(status: SellerStatus): { suspend: boolean; unsuspend: boolean } {
  return { suspend: status === "APPROVED", unsuspend: status === "SUSPENDED" };
}

export const SELLER_FILTERS = ["ALL", "PENDING", "APPROVED", "REJECTED", "SUSPENDED"] as const;
export type SellerFilter = (typeof SELLER_FILTERS)[number];
export const SELLER_FILTER_LABEL: Record<SellerFilter, string> = { ALL: "전체", PENDING: "심사 중", APPROVED: "승인", REJECTED: "반려", SUSPENDED: "정지" };
export const SELLERS_PAGE_SIZE = 20;

/** URL 의 ?status= 해석. 모르는 값은 전체. */
export function parseSellerFilter(raw: string | null): SellerFilter {
  return SELLER_FILTERS.find((f) => f === raw) ?? "ALL";
}
