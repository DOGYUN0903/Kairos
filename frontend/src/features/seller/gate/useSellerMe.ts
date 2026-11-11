"use client";

import { useQuery } from "@tanstack/react-query";
import { api, ApiError, unwrap } from "@/api/client";
import type { SellerDetail, SellerStatus } from "@/features/seller/apply/api";
import { queryKeys } from "@/lib/query-keys";

export type SellerMeState = {
  status: "loading" | "none" | SellerStatus;
  seller: SellerDetail | null;
  /** 404(미신청) 외의 실패. 호출 쪽(SellerGate)이 에러 화면을 그린다. */
  error: unknown;
  refetch: () => void;
};

/** 404 SELLER_NOT_FOUND 는 에러가 아니라 "미신청"(none)이다. */
export function useSellerMe(): SellerMeState {
  const q = useQuery({
    queryKey: queryKeys.seller.me,
    queryFn: async (): Promise<SellerDetail | null> => {
      try {
        return unwrap<SellerDetail>(await api.GET("/api/v1/seller/me"));
      } catch (e) {
        if (e instanceof ApiError && e.code === "SELLER_NOT_FOUND") return null;
        throw e;
      }
    },
    retry: (count, err) => count < 1 && !(err instanceof ApiError && err.status < 500 && err.status !== 0),
    staleTime: 30_000,
  });
  const refetch = () => void q.refetch();
  if (q.isError) return { status: "loading", seller: null, error: q.error, refetch };
  if (q.isPending) return { status: "loading", seller: null, error: null, refetch };
  if (q.data === null || q.data === undefined) return { status: "none", seller: null, error: null, refetch };
  return { status: q.data.status, seller: q.data, error: null, refetch };
}
