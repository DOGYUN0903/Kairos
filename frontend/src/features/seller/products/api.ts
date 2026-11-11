"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api, unwrap } from "@/api/client";
import { resolveError } from "@/api/errors";
import { IDEMPOTENCY_HEADER, newIdempotencyKey } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { useSellerBanner } from "@/features/seller/gate/banner";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

export type SellerProductSummary = components["schemas"]["SellerProductSummary"];
export type SellerProductDetail = components["schemas"]["SellerProductDetail"];
export type ProductStatus = components["schemas"]["ProductStatus"];
export type RegisterProductRequest = components["schemas"]["RegisterProductRequest"];
export type SellerProductPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: SellerProductSummary[] };

export const PAGE_SIZE = 20;

export type SellerProductParams = { status?: ProductStatus; page: number };

export function useSellerProducts({ status, page }: SellerProductParams) {
  const query = { ...(status ? { status } : {}), page, size: PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.seller.products(query),
    queryFn: async () => unwrap<SellerProductPage>(await api.GET("/api/v1/seller/products", { params: { query } })),
    placeholderData: keepPreviousData,
  });
}

export function useSellerProduct(productId: number, enabled = true) {
  return useQuery({
    queryKey: queryKeys.seller.product(productId),
    queryFn: async () =>
      unwrap<SellerProductDetail>(await api.GET("/api/v1/seller/products/{productId}", { params: { path: { productId } } })),
    enabled,
  });
}

export function useRegisterProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ body, key }: { body: RegisterProductRequest; key: string }) =>
      unwrap<components["schemas"]["RegisterProductResponse"]>(
        await api.POST("/api/v1/seller/products", { body, headers: { [IDEMPOTENCY_HEADER]: key } }),
      ),
    onSettled: () => qc.invalidateQueries({ queryKey: ["seller", "products"] }),
  });
}

/** 행 작업 공통: 성공 시 목록·상세 무효화 + 토스트, SELLER_NOT_ACTIVE 는 콘솔 배너, 나머지는 토스트. */
function useRowMutation<TVars>(productId: number, run: (vars: TVars) => Promise<void>, successTitle: string) {
  const qc = useQueryClient();
  const setBanner = useSellerBanner();
  return useMutation({
    mutationFn: run,
    onSuccess: () => toast({ title: successTitle }),
    onError: (e) => {
      if (e instanceof ApiError && e.code === "SELLER_NOT_ACTIVE") {
        setBanner("판매자 상태 때문에 처리할 수 없습니다.");
        void qc.invalidateQueries({ queryKey: queryKeys.seller.me });
        return;
      }
      if (e instanceof ApiError && e.code === "PRODUCT_INVALID_PRICE") return; // 다이얼로그가 필드에 붙인다.
      const { message, traceId } = resolveError(e);
      toast({ variant: "destructive", title: message, ...(traceId ? { description: `문의 시 전달: ${traceId}` } : {}) });
    },
    onSettled: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: ["seller", "products"] }),
        qc.invalidateQueries({ queryKey: queryKeys.seller.product(productId) }),
      ]),
  });
}

export function useChangePrice(productId: number) {
  return useRowMutation(
    productId,
    async ({ salePrice }: { salePrice: number }) => {
      await api.PATCH("/api/v1/seller/products/{productId}/price", { params: { path: { productId } }, body: { salePrice } });
    },
    "판매가를 변경했습니다.",
  );
}

type Transition = "pause" | "resume" | "end";
const transitionPath = {
  pause: "/api/v1/seller/products/{productId}/pause",
  resume: "/api/v1/seller/products/{productId}/resume",
  end: "/api/v1/seller/products/{productId}/end",
} as const;
const transitionToast: Record<Transition, string> = {
  pause: "판매를 일시 중지했습니다.",
  resume: "판매를 재개했습니다.",
  end: "판매를 종료했습니다.",
};

function useTransition(productId: number, kind: Transition) {
  return useRowMutation(
    productId,
    async () => {
      await api.POST(transitionPath[kind], {
        params: { path: { productId } },
        headers: { [IDEMPOTENCY_HEADER]: newIdempotencyKey() },
      });
    },
    transitionToast[kind],
  );
}

export const usePause = (productId: number) => useTransition(productId, "pause");
export const useResume = (productId: number) => useTransition(productId, "resume");
export const useEnd = (productId: number) => useTransition(productId, "end");
