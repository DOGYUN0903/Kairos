import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import { formatBusinessNumber, type ApplyValues } from "@/features/seller/apply/schemas";
import { queryKeys } from "@/lib/query-keys";

export type SellerDetail = components["schemas"]["SellerDetail"];
export type SellerStatus = components["schemas"]["SellerStatus"];

function toRequest(values: ApplyValues): components["schemas"]["SellerApplicationRequest"] {
  return { ...values, businessNumber: formatBusinessNumber(values.businessNumber) };
}

export function useApply() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ values, key }: { values: ApplyValues; key: string }) =>
      unwrap<components["schemas"]["ApplySellerResponse"]>(
        await api.POST("/api/v1/seller/application", { body: toRequest(values), headers: { [IDEMPOTENCY_HEADER]: key } }),
      ),
    onSettled: () => qc.invalidateQueries({ queryKey: queryKeys.seller.me }),
  });
}

/** PUT 은 멱등이라 Idempotency-Key 가 필요 없다. */
export function useUpdateApplication() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ values }: { values: ApplyValues }) => {
      await api.PUT("/api/v1/seller/application", { body: toRequest(values) });
    },
    onSettled: () => qc.invalidateQueries({ queryKey: queryKeys.seller.me }),
  });
}
