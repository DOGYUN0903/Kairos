import { useQueries, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type SellerProductSummary = components["schemas"]["SellerProductSummary"];
export type ProductStatus = components["schemas"]["ProductStatus"];
type SellerProductPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: SellerProductSummary[] };

const STATUSES = ["PENDING_REVIEW", "ON_SALE", "PAUSED", "REJECTED", "ENDED"] as const satisfies readonly ProductStatus[];
export type CountedStatus = (typeof STATUSES)[number];

async function fetchProducts(query: { status?: ProductStatus; size: number }): Promise<SellerProductPage> {
  return unwrap<SellerProductPage>(await api.GET("/api/v1/seller/products", { params: { query } }));
}

/** 상태별 상품 수. 각 상태를 size=1 로 조회해 totalElements 만 쓴다(별도 집계 API 없음). */
export function useStatusCounts() {
  const results = useQueries({
    queries: STATUSES.map((status) => ({
      queryKey: queryKeys.seller.products({ status, size: 1 }),
      queryFn: () => fetchProducts({ status, size: 1 }),
      select: (p: SellerProductPage) => p.totalElements,
    })),
  });
  const counts = Object.fromEntries(STATUSES.map((s, i) => [s, results[i]?.data])) as Record<CountedStatus, number | undefined>;
  return {
    counts,
    isPending: results.some((r) => r.isPending),
    isError: results.some((r) => r.isError),
    error: results.find((r) => r.isError)?.error,
    refetch: () => results.forEach((r) => void r.refetch()),
  };
}

export function useRecentProducts() {
  return useQuery({
    queryKey: queryKeys.seller.products({ size: 5 }),
    queryFn: () => fetchProducts({ size: 5 }),
  });
}
