import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { BrandValues } from "@/features/seller/brands/schemas";
import { queryKeys } from "@/lib/query-keys";

export type BrandDetail = components["schemas"]["BrandDetail"];

function toRequest(v: BrandValues): components["schemas"]["BrandRequest"] {
  return {
    name: v.name,
    englishName: v.englishName === "" ? null : v.englishName,
    logoFileId: v.logoFileId,
    description: v.description === "" ? null : v.description,
  };
}

export function useBrands() {
  return useQuery({
    queryKey: queryKeys.seller.brands,
    queryFn: async () => unwrap<BrandDetail[]>(await api.GET("/api/v1/seller/brands")),
  });
}

export function useCreateBrand() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ values, key }: { values: BrandValues; key: string }) =>
      unwrap<components["schemas"]["ApplyBrandResponse"]>(
        await api.POST("/api/v1/seller/brands", { body: toRequest(values), headers: { [IDEMPOTENCY_HEADER]: key } }),
      ),
    onSettled: () => qc.invalidateQueries({ queryKey: queryKeys.seller.brands }),
  });
}

/** 반려된 브랜드 재신청(PUT, 멱등이라 키 없음). */
export function useUpdateBrand(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ values }: { values: BrandValues }) => {
      await api.PUT("/api/v1/seller/brands/{brandId}", { params: { path: { brandId: id } }, body: toRequest(values) });
    },
    onSettled: () => qc.invalidateQueries({ queryKey: queryKeys.seller.brands }),
  });
}
