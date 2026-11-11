"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { api, ApiError, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { resolveError } from "@/api/errors";
import { useAuth } from "@/auth/useAuth";
import { toast } from "@/hooks/use-toast";
import type { ProductListParams } from "@/features/catalog/search-params";
import { queryKeys } from "@/lib/query-keys";

export type CategoryNode = components["schemas"]["CategoryNode"];
export type ProductSummary = components["schemas"]["ProductSummary"];
export type ProductDetail = components["schemas"]["ProductDetail"];
export type BrandListItem = components["schemas"]["BrandListItem"];
export type BrandPublic = components["schemas"]["BrandPublic"];
export type ProductPage = components["schemas"]["PageResponse"] & { content?: ProductSummary[] };

const noRetryOn404 = (count: number, err: unknown) => count < 1 && !(err instanceof ApiError && err.status === 404);

export function useCategories() {
  return useQuery({
    queryKey: queryKeys.catalog.categories,
    queryFn: async () => unwrap<CategoryNode[]>(await api.GET("/api/v1/categories")),
    staleTime: 5 * 60_000,
  });
}

export function useProducts(params: ProductListParams) {
  return useQuery({
    queryKey: queryKeys.catalog.products(params),
    queryFn: async () => unwrap<ProductPage>(await api.GET("/api/v1/products", { params: { query: params } })),
    placeholderData: keepPreviousData,
  });
}

/** liked 는 로그인 여부에 따라 달라지므로, 인증 부트스트랩이 끝난 뒤에 불러온다. */
export function useProduct(productId: number) {
  const { status, me } = useAuth();
  return useQuery({
    queryKey: [...queryKeys.catalog.product(productId), me?.memberId ?? null],
    queryFn: async () =>
      unwrap<ProductDetail>(await api.GET("/api/v1/products/{productId}", { params: { path: { productId } } })),
    enabled: status !== "loading",
    retry: noRetryOn404,
  });
}

/** 목록 필터용 공개 브랜드 목록(이름순, 최대 200). */
export function useBrands() {
  return useQuery({
    queryKey: queryKeys.catalog.brands,
    queryFn: async () => unwrap<BrandListItem[]>(await api.GET("/api/v1/brands")),
    staleTime: 5 * 60_000,
  });
}

export function useBrand(brandId: number) {
  return useQuery({
    queryKey: queryKeys.catalog.brand(brandId),
    queryFn: async () => unwrap<BrandPublic>(await api.GET("/api/v1/brands/{brandId}", { params: { path: { brandId } } })),
    retry: noRetryOn404,
  });
}

/** 좋아요 토글. 비로그인이면 호출 없이 로그인 화면으로 보낸다. 성공하면 상세·목록을 다시 불러온다. */
export function useToggleLike(productId: number) {
  const { status } = useAuth();
  const router = useRouter();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: async (currentlyLiked: boolean) => {
      const params = { params: { path: { productId } } };
      if (currentlyLiked) await api.DELETE("/api/v1/products/{productId}/like", params);
      else await api.PUT("/api/v1/products/{productId}/like", params);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.catalog.product(productId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.catalog.productsRoot }),
      ]);
    },
    onError: (e) => {
      const { message } = resolveError(e);
      toast({ variant: "destructive", title: message || "좋아요를 처리하지 못했습니다." });
    },
  });

  const toggle = (currentlyLiked: boolean) => {
    if (mutation.isPending || status === "loading") return;
    if (status === "anonymous") {
      const next = window.location.pathname + window.location.search;
      router.push(`/login?next=${encodeURIComponent(next)}`);
      return;
    }
    mutation.mutate(currentlyLiked);
  };

  return { toggle, isPending: mutation.isPending };
}
