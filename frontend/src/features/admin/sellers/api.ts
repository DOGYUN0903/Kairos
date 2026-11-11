"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";
import { ApiError, api, unwrap } from "@/api/client";
import { resolveError } from "@/api/errors";
import type { components } from "@/api/schema";
import { SELLERS_PAGE_SIZE, type SellerFilter } from "@/features/admin/sellers/actions";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

export type AdminSeller = components["schemas"]["SellerDetail"];
export type SellersPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: AdminSeller[] };
export type SellerListParams = { status: SellerFilter; page: number };

export function useAdminSellers(params: SellerListParams) {
  const query = { ...(params.status === "ALL" ? {} : { status: params.status }), sort: "LATEST" as const, page: params.page, size: SELLERS_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.admin.sellers(params),
    queryFn: async () => unwrap<SellersPage>(await api.GET("/api/v1/admin/sellers", { params: { query } })),
    placeholderData: (prev) => prev,
  });
}

/** 정지·해제 뒤에는 목록·상세·심사 큐 수를 다시 읽는다. */
function useAfterChange() {
  const qc = useQueryClient();
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ["admin", "sellers"] }),
      qc.invalidateQueries({ queryKey: ["admin", "seller"] }),
      qc.invalidateQueries({ queryKey: ["admin", "queue"] }),
      qc.invalidateQueries({ queryKey: ["admin", "queueCount"] }),
    ]);
}

function useSellerAction(kind: "suspend" | "unsuspend") {
  const afterChange = useAfterChange();
  const inFlight = useRef(false);
  const mutation = useMutation({
    mutationFn: async ({ id, reason }: { id: number; reason?: string }) => {
      if (kind === "suspend") await api.POST("/api/v1/admin/sellers/{sellerId}/suspend", { params: { path: { sellerId: id } }, body: { reason: reason ?? "" } });
      else await api.POST("/api/v1/admin/sellers/{sellerId}/unsuspend", { params: { path: { sellerId: id } } });
    },
    onSuccess: () => toast({ title: kind === "suspend" ? "판매자를 정지했습니다." : "정지를 해제했습니다." }),
    onError: (e) => {
      if (e instanceof ApiError && e.code === "SELLER_INVALID_STATE_TRANSITION") {
        toast({ title: "이미 처리된 건입니다. 목록을 새로 불러옵니다." });
        return;
      }
      const { message, traceId } = resolveError(e);
      toast({ variant: "destructive", title: message, ...(traceId ? { description: `문의 시 전달: ${traceId}` } : {}) });
    },
    onSettled: afterChange,
  });
  /** 처리 중 두 번째 호출은 무시한다. 성공 여부를 돌려준다. */
  const submit = async (v: { id: number; reason?: string }): Promise<boolean> => {
    if (inFlight.current) return false;
    inFlight.current = true;
    try {
      await mutation.mutateAsync(v);
      return true;
    } catch {
      return false;
    } finally {
      inFlight.current = false;
    }
  };
  return { submit, isPending: mutation.isPending };
}

export const useSuspend = () => useSellerAction("suspend");
export const useUnsuspend = () => useSellerAction("unsuspend");
