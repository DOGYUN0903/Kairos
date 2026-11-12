"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type OrderQuote = components["schemas"]["OrderQuote"];
export type QuoteLine = components["schemas"]["QuoteLine"];
export type SellerQuote = components["schemas"]["SellerQuote"];

/** 서버 견적. 금액은 모두 서버 값이다. 이전 응답은 placeholder 로만 쓰고 키가 바뀌면 새로 부른다. */
export function useQuote(items: { skuId: number; quantity: number }[], enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.checkout.quote(items),
    queryFn: async () => unwrap<OrderQuote>(await api.POST("/api/v1/orders/quote", { body: { items, pointsToUse: 0 } })),
    enabled: enabled && items.length > 0,
    placeholderData: keepPreviousData,
    retry: false,
  });
}
