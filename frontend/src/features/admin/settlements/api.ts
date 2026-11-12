"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import type { CreateSettlementAdjustmentRequest } from "@/features/admin/settlements/adjustment-schema";
import type { GenerateSettlementResult } from "@/features/admin/settlements/generate";
import type { AdminSettlementParams } from "@/features/admin/settlements/params";
import type { SettlementDetail, SettlementSummary } from "@/features/seller/settlements/api";
import { queryKeys } from "@/lib/query-keys";

export type { SettlementDetail, SettlementSummary };
export type SettlementAdjustment = components["schemas"]["SettlementAdjustmentResult"];
type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export const ADMIN_SETTLEMENT_PAGE_SIZE = 20;
export const ADMIN_ADJUSTMENT_PAGE_SIZE = 20;

export function useAdminSettlements({ sellerId, status, page }: AdminSettlementParams) {
  const query = { ...(sellerId !== undefined ? { sellerId } : {}), ...(status ? { status } : {}), page, size: ADMIN_SETTLEMENT_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.adminSettlements.list(query),
    queryFn: async () => unwrap<Page<SettlementSummary>>(await api.GET("/api/v1/admin/settlements", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

export function useAdminSettlement(settlementId: number) {
  return useQuery({
    queryKey: queryKeys.adminSettlements.detail(settlementId),
    queryFn: async () => unwrap<SettlementDetail>(await api.GET("/api/v1/admin/settlements/{settlementId}", { params: { path: { settlementId } } })),
    enabled: Number.isSafeInteger(settlementId) && settlementId > 0,
    staleTime: 0,
  });
}

/**
 * 주간 정산 생성. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다).
 * 성공이든 실패든(결과를 모를 수도 있다) 목록을 다시 읽게 한다.
 */
export function useGenerateSettlements() {
  const qc = useQueryClient();
  return async (periodStart: string, key: string): Promise<GenerateSettlementResult> => {
    try {
      return unwrap<GenerateSettlementResult>(await api.POST("/api/v1/admin/settlements/generate", { params: { header: { [IDEMPOTENCY_HEADER]: key } }, body: { periodStart } }));
    } finally {
      void qc.invalidateQueries({ queryKey: queryKeys.adminSettlements.lists });
    }
  };
}

/** 확정·지급 완료. 키 없는 멱등 PATCH 라 호출하는 쪽이 처리 중 재호출을 막는다. 끝나면(성공·실패 모두) 상세와 목록을 다시 읽는다. */
export function useAdminSettlementActions(settlementId: number) {
  const qc = useQueryClient();
  const refresh = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: queryKeys.adminSettlements.detail(settlementId) }),
      qc.invalidateQueries({ queryKey: queryKeys.adminSettlements.lists }),
    ]);
  };
  return {
    confirm: async (): Promise<void> => {
      try {
        await api.PATCH("/api/v1/admin/settlements/{settlementId}/confirm", { params: { path: { settlementId } } });
      } finally {
        await refresh();
      }
    },
    pay: async (memo: string | undefined): Promise<void> => {
      try {
        await api.PATCH("/api/v1/admin/settlements/{settlementId}/pay", { params: { path: { settlementId } }, body: memo ? { memo } : {} });
      } finally {
        await refresh();
      }
    },
  };
}

/** 판매자 정산 조정 목록. sellerId 가 null 이면 조회하지 않는다. */
export function useAdminAdjustments(sellerId: number | null, page: number) {
  const query = { page, size: ADMIN_ADJUSTMENT_PAGE_SIZE };
  return useQuery({
    queryKey: [...queryKeys.adminAdjustments(sellerId ?? 0), query] as const,
    queryFn: async () =>
      unwrap<Page<SettlementAdjustment>>(await api.GET("/api/v1/admin/sellers/{sellerId}/settlement-adjustments", { params: { path: { sellerId: sellerId as number }, query } })),
    placeholderData: keepPreviousData,
    enabled: sellerId !== null,
    staleTime: 0,
  });
}

/** 조정 등록(키 필수) · 삭제(키 없음). 끝나면(성공·실패 모두) 그 판매자의 조정 목록을 다시 읽는다. */
export function useAdminAdjustmentActions(sellerId: number) {
  const qc = useQueryClient();
  const refresh = () => qc.invalidateQueries({ queryKey: queryKeys.adminAdjustments(sellerId) });
  return {
    create: async (body: CreateSettlementAdjustmentRequest, key: string): Promise<void> => {
      try {
        unwrap<unknown>(await api.POST("/api/v1/admin/sellers/{sellerId}/settlement-adjustments", { params: { path: { sellerId }, header: { [IDEMPOTENCY_HEADER]: key } }, body }));
      } finally {
        void refresh();
      }
    },
    remove: async (adjustmentId: number): Promise<void> => {
      try {
        await api.DELETE("/api/v1/admin/settlement-adjustments/{adjustmentId}", { params: { path: { adjustmentId } } });
      } finally {
        await refresh();
      }
    },
  };
}
