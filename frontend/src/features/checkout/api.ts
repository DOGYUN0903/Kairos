"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type OrderQuote = components["schemas"]["OrderQuote"];
export type QuoteLine = components["schemas"]["QuoteLine"];
export type SellerQuote = components["schemas"]["SellerQuote"];
export type CreateOrderRequest = components["schemas"]["CreateOrderRequest"];
export type CreateOrderResponse = components["schemas"]["CreateOrderResponse"];
export type OrderShippingAddress = components["schemas"]["OrderShippingAddress"];

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

/** 주문 생성. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다). 성공하면 주문 목록을 무효화한다(장바구니는 결제 승인 때 서버가 비우므로 여기서 건드리지 않는다). */
export function useCreateOrder() {
  const qc = useQueryClient();
  return async (body: CreateOrderRequest, key: string): Promise<CreateOrderResponse> => {
    const res = unwrap<CreateOrderResponse>(await api.POST("/api/v1/orders", { params: { header: { [IDEMPOTENCY_HEADER]: key } }, body }));
    void qc.invalidateQueries({ queryKey: queryKeys.orders.root });
    return res;
  };
}

/** 가장 최근 주문의 배송지. 주문이 없으면 null. 버튼을 누를 때 mutate 한다(저장된 배송지는 없다). */
export function useRecentShippingAddress() {
  return useMutation({
    mutationFn: async (): Promise<OrderShippingAddress | null> => {
      const page = unwrap<{ content?: components["schemas"]["OrderSummary"][] }>(
        await api.GET("/api/v1/orders", { params: { query: { page: 0, size: 1 } } }),
      );
      const first = page.content?.[0];
      if (!first) return null;
      const detail = unwrap<components["schemas"]["OrderDetail"]>(
        await api.GET("/api/v1/orders/{orderId}", { params: { path: { orderId: first.orderId } } }),
      );
      return detail.shippingAddress ?? null;
    },
  });
}
