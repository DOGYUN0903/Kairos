"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { AdminReturnParams } from "@/features/admin/returns/params";
import type { ReturnPage, ReturnRequestResponse } from "@/features/returns/api";
import { queryKeys } from "@/lib/query-keys";

export const ADMIN_RETURN_PAGE_SIZE = 20;

export function useAdminReturns({ status, sellerId, memberId, page }: AdminReturnParams) {
  const query = {
    ...(status ? { status } : {}),
    ...(sellerId !== undefined ? { sellerId } : {}),
    ...(memberId !== undefined ? { memberId } : {}),
    page,
    size: ADMIN_RETURN_PAGE_SIZE,
  };
  return useQuery({
    queryKey: queryKeys.adminReturns.list(query),
    queryFn: async () => unwrap<ReturnPage>(await api.GET("/api/v1/admin/returns", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 관리자 반품 상세. 상태가 바뀌므로 마운트마다 다시 읽는다. refetchInterval 은 환불 확인 중의 재조회에 쓴다. */
export function useAdminReturn(returnId: number, opts?: { refetchInterval?: number | false }) {
  return useQuery({
    queryKey: queryKeys.adminReturns.detail(returnId),
    queryFn: async () =>
      unwrap<ReturnRequestResponse>(await api.GET("/api/v1/admin/returns/{returnId}", { params: { path: { returnId } } })),
    enabled: Number.isSafeInteger(returnId) && returnId > 0,
    staleTime: 0,
    refetchInterval: opts?.refetchInterval ?? false,
  });
}

/**
 * 승인·대신 수거 확인·중단. 셋 다 키 없는 PATCH 다(서버가 멱등). 실패는 던진다.
 * 성공이든 실패든(서버가 답했거나 결과를 모르거나) 상세·목록을 다시 읽게 한다. 자동 재시도는 없다.
 */
export function useAdminReturnActions(returnId: number) {
  const qc = useQueryClient();
  const invalidate = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: queryKeys.adminReturns.detail(returnId) }),
      qc.invalidateQueries({ queryKey: queryKeys.adminReturns.lists }),
    ]);
  const path = { params: { path: { returnId } } };
  const run = async (call: () => Promise<{ data?: { data?: ReturnRequestResponse } }>): Promise<ReturnRequestResponse> => {
    try {
      return unwrap<ReturnRequestResponse>(await call());
    } finally {
      void invalidate();
    }
  };
  return {
    approve: () => run(() => api.PATCH("/api/v1/admin/returns/{returnId}/approve", path)),
    receive: () => run(() => api.PATCH("/api/v1/admin/returns/{returnId}/receive", path)),
    cancel: () => run(() => api.PATCH("/api/v1/admin/returns/{returnId}/cancel", path)),
  };
}
