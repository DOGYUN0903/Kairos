"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { toBenefitBody, type BenefitSelection } from "@/features/checkout/benefit-selection";
import { queryKeys } from "@/lib/query-keys";

export type OrderQuote = components["schemas"]["OrderQuote"];
export type QuoteCoupon = components["schemas"]["QuoteCoupon"];
export type QuoteLine = components["schemas"]["QuoteLine"];
export type SellerQuote = components["schemas"]["SellerQuote"];
export type CreateOrderRequest = components["schemas"]["CreateOrderRequest"];
export type CreateOrderResponse = components["schemas"]["CreateOrderResponse"];
export type OrderShippingAddress = components["schemas"]["OrderShippingAddress"];

/** 견적과 그 견적을 만든 선택. 둘은 같은 응답 안에 묶여 있어 어긋날 수 없다. */
export type QuoteResult = { quote: OrderQuote; selection: BenefitSelection };

/**
 * 서버 견적. 금액은 모두 서버 값이다. 이전 응답은 placeholder 로만 쓰고 키가 바뀌면 새로 부른다.
 * 키와 본문은 모두 toBenefitBody(selection) 이고, 응답에 그 selection 을 함께 담는다(제출이 같은 견적에서 금액·선택을 꺼낸다).
 */
export function useQuote(items: { skuId: number; quantity: number }[], selection: BenefitSelection, enabled: boolean) {
  const benefit = toBenefitBody(selection);
  return useQuery({
    queryKey: queryKeys.checkout.quote(items, benefit),
    queryFn: async (): Promise<QuoteResult> => ({
      quote: unwrap<OrderQuote>(await api.POST("/api/v1/orders/quote", { body: { items, ...benefit } })),
      selection,
    }),
    enabled: enabled && items.length > 0,
    // 견적은 캐시에서 내주지 않는다: 다시 마운트하거나 선택으로 돌아와도 항상 서버에 다시 묻는다.
    staleTime: 0,
    placeholderData: keepPreviousData,
    retry: false,
  });
}

/** 주문 생성. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다). 성공하면 주문 목록·장바구니·내 쿠폰·포인트·견적 캐시를 무효화한다(0원 주문은 PAID 로 바로 완료 화면에 가서 결제 승인 화면을 거치지 않으므로 장바구니도 여기서 다시 읽게 한다). */
export function useCreateOrder() {
  const qc = useQueryClient();
  return async (body: CreateOrderRequest, key: string): Promise<CreateOrderResponse> => {
    const res = unwrap<CreateOrderResponse>(await api.POST("/api/v1/orders", { params: { header: { [IDEMPOTENCY_HEADER]: key } }, body }));
    void qc.invalidateQueries({ queryKey: queryKeys.orders.root });
    // 쿠폰은 사용 처리되고 포인트는 차감된다. 장바구니는 서버가 비웠을 수 있고, 캐시된 견적은 쓴 쿠폰을 아직 쓸 수 있다고 보일 수 있다.
    void qc.invalidateQueries({ queryKey: queryKeys.cart });
    void qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes });
    void qc.invalidateQueries({ queryKey: queryKeys.coupons.mineRoot });
    void qc.invalidateQueries({ queryKey: queryKeys.points.root });
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
