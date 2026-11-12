"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type OrderDetail = components["schemas"]["OrderDetail"];
export type SellerOrderResult = components["schemas"]["SellerOrderResult"];
export type OrderItemResult = components["schemas"]["OrderItemResult"];
export type OrderSummary = components["schemas"]["OrderSummary"];
export type OrderSummaryPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: OrderSummary[] };
export type CancelPreview = components["schemas"]["CancelPreview"];
export type CancelItemResponse = components["schemas"]["CancelItemResponse"];

export const ORDER_PAGE_SIZE = 10;

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

/** 내 주문 목록. ?page= (0부터)가 진실이고 이전 페이지는 placeholder 로만 쓴다. */
export function useMyOrders(page: number) {
  const query = { page, size: ORDER_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.orders.list(query),
    queryFn: async () => unwrap<OrderSummaryPage>(await api.GET("/api/v1/orders", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 취소 환불 미리보기. 창이 열려 있을 때만 읽고, 409(취소 불가)는 재시도하지 않는다. 캐시하지 않는다(금액은 항상 새로). */
export function useCancelPreview(orderId: number, orderItemId: number, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.orders.cancelPreview(orderId, orderItemId),
    queryFn: async () =>
      unwrap<CancelPreview>(
        await api.GET("/api/v1/orders/{orderId}/items/{orderItemId}/cancel-preview", { params: { path: { orderId, orderItemId } } }),
      ),
    enabled,
    retry: false,
    staleTime: 0,
    gcTime: 0,
  });
}

/** 주문 상세와 목록을 다시 읽게 한다(취소·확정 뒤). */
export function useInvalidateOrders(orderId: number) {
  const qc = useQueryClient();
  return () => Promise.all([qc.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) }), qc.invalidateQueries({ queryKey: queryKeys.orders.list() })]);
}

/**
 * 항목 취소. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다).
 * 무효화는 여기 한 곳에서 한다: 성공이든 실패든(서버가 답했거나 결과를 모르거나) 주문 상세와 목록을 다시 읽게 한다.
 */
export function useCancelItem(orderId: number) {
  const invalidate = useInvalidateOrders(orderId);
  return async (orderItemId: number, reason: string, key: string): Promise<CancelItemResponse> => {
    try {
      return unwrap<CancelItemResponse>(
        await api.POST("/api/v1/orders/{orderId}/items/{orderItemId}/cancel", {
          params: { path: { orderId, orderItemId }, header: { [IDEMPOTENCY_HEADER]: key } },
          body: { reason },
        }),
      );
    } finally {
      void invalidate();
    }
  };
}

/** 구매 확정. 키 없음(서버가 멱등). 실패는 던지고, 성공하면 주문 상세와 목록을 무효화한다. */
export function useConfirmPurchase(orderId: number) {
  const invalidate = useInvalidateOrders(orderId);
  return async (sellerOrderId: number): Promise<void> => {
    unwrap<unknown>(
      await api.PATCH("/api/v1/orders/{orderId}/seller-orders/{sellerOrderId}/confirm", { params: { path: { orderId, sellerOrderId } } }),
    );
    await invalidate();
  };
}
