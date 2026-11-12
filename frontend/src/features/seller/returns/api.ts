"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { ReturnPage, ReturnRequestResponse } from "@/features/returns/api";
import type { ReturnTabStatus } from "@/features/seller/returns/tabs";
import { queryKeys } from "@/lib/query-keys";

export const SELLER_RETURN_PAGE_SIZE = 20;

export type SellerReturnParams = { status?: ReturnTabStatus; page: number };

/** 판매자 반품 목록. ?status=&page= 가 진실이고 이전 페이지는 placeholder 로만 쓴다. */
export function useSellerReturns({ status, page }: SellerReturnParams) {
  const query = { ...(status ? { status } : {}), page, size: SELLER_RETURN_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.sellerReturns.list(query),
    queryFn: async () => unwrap<ReturnPage>(await api.GET("/api/v1/seller/returns", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 판매자 반품 상세. 상태가 바뀌므로 마운트마다 다시 읽는다. refetchInterval 은 환불 확인 중의 재조회에 쓴다. */
export function useSellerReturn(returnId: number, opts?: { refetchInterval?: number | false }) {
  return useQuery({
    queryKey: queryKeys.sellerReturns.detail(returnId),
    queryFn: async () =>
      unwrap<ReturnRequestResponse>(await api.GET("/api/v1/seller/returns/{returnId}", { params: { path: { returnId } } })),
    enabled: Number.isSafeInteger(returnId) && returnId > 0,
    staleTime: 0,
    refetchInterval: opts?.refetchInterval ?? false,
  });
}

/** 처리할 반품(REQUESTED) 건수. 별도 집계 API 가 없어 size=1 로 읽은 totalElements 를 쓴다(주문 관리 배지와 같은 방식). */
export const PENDING_RETURN_COUNT_QUERY = { status: "REQUESTED", page: 0, size: 1 } as const;

/**
 * 키는 목록 키(lists 접두사 아래)라 승인·거절·수거 확인 뒤 lists 를 무효화하면 배지도 함께 다시 읽는다.
 * 로딩·실패 중에는 data 가 undefined 이므로 호출 쪽이 배지를 그리지 않는다.
 */
export function useSellerReturnCount(enabled = true) {
  return useQuery({
    queryKey: queryKeys.sellerReturns.list(PENDING_RETURN_COUNT_QUERY),
    queryFn: async () => unwrap<ReturnPage>(await api.GET("/api/v1/seller/returns", { params: { query: PENDING_RETURN_COUNT_QUERY } })),
    select: (p: ReturnPage) => p.totalElements,
    enabled,
    staleTime: 0,
  });
}

/** 상세·목록(처리할 건수 배지 포함)을 다시 읽게 한다. */
export function useInvalidateSellerReturns(returnId: number) {
  const qc = useQueryClient();
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: queryKeys.sellerReturns.detail(returnId) }),
      qc.invalidateQueries({ queryKey: queryKeys.sellerReturns.lists }),
    ]);
}

/**
 * 승인·거절·수거 확인. 셋 다 키 없는 PATCH 다(서버가 멱등). 실패는 던진다.
 * 성공이든 실패든(서버가 답했거나 결과를 모르거나) 상세·목록을 다시 읽게 한다. 자동 재시도는 없다.
 */
export function useSellerReturnActions(returnId: number) {
  const invalidate = useInvalidateSellerReturns(returnId);
  const path = { params: { path: { returnId } } };
  const run = async (call: () => Promise<{ data?: { data?: ReturnRequestResponse } }>): Promise<ReturnRequestResponse> => {
    try {
      return unwrap<ReturnRequestResponse>(await call());
    } finally {
      void invalidate();
    }
  };
  return {
    approve: () => run(() => api.PATCH("/api/v1/seller/returns/{returnId}/approve", path)),
    reject: (rejectReason: string) => run(() => api.PATCH("/api/v1/seller/returns/{returnId}/reject", { ...path, body: { rejectReason } })),
    receive: () => run(() => api.PATCH("/api/v1/seller/returns/{returnId}/receive", path)),
  };
}
