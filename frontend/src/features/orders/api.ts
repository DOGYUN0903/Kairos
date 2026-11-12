"use client";

import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type OrderDetail = components["schemas"]["OrderDetail"];
export type SellerOrderResult = components["schemas"]["SellerOrderResult"];
export type OrderItemResult = components["schemas"]["OrderItemResult"];

/**
 * 구매자 주문 상세. 결제·취소 상태를 보고 화면을 정하므로 캐시를 신선하다고 보지 않는다(마운트마다 다시 읽는다).
 * refetchInterval 은 확인 중 상태의 재조회에 쓴다(features/orders/polling.ts).
 */
export function useOrder(orderId: number, opts?: { refetchInterval?: number | false }) {
  return useQuery({
    queryKey: queryKeys.orders.detail(orderId),
    queryFn: async () => unwrap<OrderDetail>(await api.GET("/api/v1/orders/{orderId}", { params: { path: { orderId } } })),
    enabled: Number.isSafeInteger(orderId) && orderId > 0,
    staleTime: 0,
    refetchInterval: opts?.refetchInterval ?? false,
  });
}
