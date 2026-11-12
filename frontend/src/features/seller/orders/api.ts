"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import type { CancelItemResponse } from "@/features/orders/api";
import { queryKeys } from "@/lib/query-keys";

export type SellerOrderSummary = components["schemas"]["SellerOrderSummary"];
export type SellerOrderDetail = components["schemas"]["SellerOrderDetail"];
export type SellerOrderStatus = components["schemas"]["SellerOrderStatus"];
export type SellerOrderPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: SellerOrderSummary[] };

export const SELLER_ORDER_PAGE_SIZE = 20;

export type SellerOrderParams = { status?: SellerOrderStatus; page: number };

/** 판매자 주문 목록. ?status=&page= 가 진실이고 이전 페이지는 placeholder 로만 쓴다. */
export function useSellerOrders({ status, page }: SellerOrderParams) {
  const query = { ...(status ? { status } : {}), page, size: SELLER_ORDER_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.sellerOrders.list(query),
    queryFn: async () => unwrap<SellerOrderPage>(await api.GET("/api/v1/seller/orders", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 판매자 주문 상세. 상태가 바뀌는 화면이라 마운트마다 다시 읽는다. */
export function useSellerOrder(id: number) {
  return useQuery({
    queryKey: queryKeys.sellerOrders.detail(id),
    queryFn: async () =>
      unwrap<SellerOrderDetail>(await api.GET("/api/v1/seller/orders/{sellerOrderId}", { params: { path: { sellerOrderId: id } } })),
    enabled: Number.isSafeInteger(id) && id > 0,
    staleTime: 0,
  });
}

/** 상태별 건수. 별도 집계 API 가 없어 size=1 로 읽은 totalElements 를 쓴다(검수 배지와 같은 방식). */
export function useSellerOrderCount(status: SellerOrderStatus, enabled = true) {
  return useQuery({
    queryKey: queryKeys.sellerOrders.count(status),
    queryFn: async () =>
      unwrap<SellerOrderPage>(await api.GET("/api/v1/seller/orders", { params: { query: { status, page: 0, size: 1 } } })),
    select: (p: SellerOrderPage) => p.totalElements,
    enabled,
    staleTime: 0,
  });
}

/** 상세·목록·건수를 다시 읽게 한다(발송·배송 완료·취소 뒤). */
export function useInvalidateSellerOrders(id: number) {
  const qc = useQueryClient();
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: queryKeys.sellerOrders.detail(id) }),
      qc.invalidateQueries({ queryKey: queryKeys.sellerOrders.lists }),
      qc.invalidateQueries({ queryKey: queryKeys.sellerOrders.counts }),
    ]);
}

/** 발송 처리. 키 없음(서버가 같은 송장은 200, 다른 송장은 409). 실패는 던지고, 서버가 답했다면(성공·거절) 상세·목록·건수를 무효화한다. */
export function useShip(id: number) {
  const invalidate = useInvalidateSellerOrders(id);
  return async ({ carrierCode, trackingNumber }: { carrierCode: string; trackingNumber: string }): Promise<void> => {
    try {
      unwrap<unknown>(
        await api.PATCH("/api/v1/seller/orders/{sellerOrderId}/ship", {
          params: { path: { sellerOrderId: id } },
          body: { carrierCode, trackingNumber },
        }),
      );
    } catch (e) {
      void invalidate();
      throw e;
    }
    await invalidate();
  };
}

/** 배송 완료 처리. 키 없음. */
export function useDeliver(id: number) {
  const invalidate = useInvalidateSellerOrders(id);
  return async (): Promise<void> => {
    try {
      unwrap<unknown>(await api.PATCH("/api/v1/seller/orders/{sellerOrderId}/deliver", { params: { path: { sellerOrderId: id } } }));
    } catch (e) {
      void invalidate();
      throw e;
    }
    await invalidate();
  };
}

/**
 * 판매자 항목 취소. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다).
 * 무효화는 여기 한 곳에서 한다: 성공이든 실패든(서버가 답했거나 결과를 모르거나) 상세·목록·건수를 다시 읽게 한다.
 */
export function useSellerCancelItem(id: number) {
  const invalidate = useInvalidateSellerOrders(id);
  return async (orderItemId: number, reason: string, key: string): Promise<CancelItemResponse> => {
    try {
      return unwrap<CancelItemResponse>(
        await api.POST("/api/v1/seller/orders/{sellerOrderId}/items/{orderItemId}/cancel", {
          params: { path: { sellerOrderId: id, orderItemId }, header: { [IDEMPOTENCY_HEADER]: key } },
          body: { reason },
        }),
      );
    } finally {
      void invalidate();
    }
  };
}
