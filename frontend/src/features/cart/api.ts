"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { resolveError } from "@/api/errors";
import { useAuth } from "@/auth/useAuth";
import { useIdempotentAction } from "@/hooks/useIdempotentAction";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

export type Cart = components["schemas"]["CartResult"];
export type CartItem = components["schemas"]["CartItemResult"];

export function useCart() {
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.cart,
    queryFn: async () => unwrap<Cart>(await api.GET("/api/v1/cart")),
    enabled: status === "authenticated",
  });
}

/** 헤더 배지용 항목 수. 비로그인이거나 아직 못 읽었으면 null. */
export function useCartCount(): number | null {
  const { data } = useCart();
  return data ? data.items.length : null;
}

/** 담기. 성공하면 장바구니를 다시 읽는다. 실패는 onError 로 넘긴다(토스트는 호출 쪽). */
export function useAddCartItem(opts?: { onSuccess?: () => void; onError?: (e: unknown) => void }) {
  const qc = useQueryClient();
  return useIdempotentAction(
    async ({ skuId, quantity }: { skuId: number; quantity: number }, key: string) => {
      await api.POST("/api/v1/cart/items", {
        params: { header: { [IDEMPOTENCY_HEADER]: key } },
        body: { skuId, quantity },
      });
      await qc.invalidateQueries({ queryKey: queryKeys.cart });
      opts?.onSuccess?.();
    },
    {
      onError:
        opts?.onError ??
        ((e) => toast({ variant: "destructive", title: resolveError(e).message || "장바구니에 담지 못했습니다." })),
    },
  );
}

/** 수량 변경. 키 없이 PATCH. 응답 뒤 장바구니·견적을 무효화한다(낙관적 갱신 없음, 자동 재시도 없음). */
export function useChangeCartQuantity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ cartItemId, quantity }: { cartItemId: number; quantity: number }) => {
      await api.PATCH("/api/v1/cart/items/{cartItemId}", { params: { path: { cartItemId } }, body: { quantity } });
    },
    onError: (e) => toast({ variant: "destructive", title: resolveError(e).message || "수량을 바꾸지 못했습니다." }),
    onSettled: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.cart }),
        qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes }),
      ]);
    },
  });
}

export function useRemoveCartItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (cartItemId: number) => {
      await api.DELETE("/api/v1/cart/items/{cartItemId}", { params: { path: { cartItemId } } });
    },
    onError: (e) => toast({ variant: "destructive", title: resolveError(e).message || "삭제하지 못했습니다." }),
    onSettled: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: queryKeys.cart }),
        qc.invalidateQueries({ queryKey: queryKeys.checkout.quotes }),
      ]);
    },
  });
}
