"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import type { CancelItemResponse, OrderDetail, OrderSummaryPage } from "@/features/orders/api";
import type { AdminOrderParams } from "@/features/admin/orders/params";
import { queryKeys } from "@/lib/query-keys";

export type OrderStatus = components["schemas"]["OrderStatus"];

export const ADMIN_ORDER_PAGE_SIZE = 20;

export function useAdminOrders({ status, memberId, orderNo, page }: AdminOrderParams) {
  const query = {
    ...(status ? { status } : {}),
    ...(memberId !== undefined ? { memberId } : {}),
    ...(orderNo ? { orderNo } : {}),
    page,
    size: ADMIN_ORDER_PAGE_SIZE,
  };
  return useQuery({
    queryKey: queryKeys.adminOrders.list(query),
    queryFn: async () => unwrap<OrderSummaryPage>(await api.GET("/api/v1/admin/orders", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 관리자 주문 상세. 확인 중 상태의 재조회를 위해 refetchInterval 을 받는다(features/orders/polling.ts). */
export function useAdminOrder(orderId: number, opts?: { refetchInterval?: number | false }) {
  return useQuery({
    queryKey: queryKeys.adminOrders.detail(orderId),
    queryFn: async () => unwrap<OrderDetail>(await api.GET("/api/v1/admin/orders/{orderId}", { params: { path: { orderId } } })),
    enabled: Number.isSafeInteger(orderId) && orderId > 0,
    staleTime: 0,
    refetchInterval: opts?.refetchInterval ?? false,
  });
}

/** 항목 취소. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다). 서버가 답했다면(성공·거절) 상세와 목록을 무효화한다. */
export function useAdminCancelItem(orderId: number) {
  const qc = useQueryClient();
  const invalidate = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: queryKeys.adminOrders.detail(orderId) }),
      qc.invalidateQueries({ queryKey: ["adminOrders", "list"] }),
    ]);
  return async (orderItemId: number, reason: string, key: string): Promise<CancelItemResponse> => {
    try {
      const res = unwrap<CancelItemResponse>(
        await api.POST("/api/v1/admin/orders/{orderId}/items/{orderItemId}/cancel", {
          params: { path: { orderId, orderItemId }, header: { [IDEMPOTENCY_HEADER]: key } },
          body: { reason },
        }),
      );
      void invalidate();
      return res;
    } catch (e) {
      void invalidate();
      throw e;
    }
  };
}
