"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type SettlementSummary = components["schemas"]["SettlementSummary"];
export type SettlementDetail = components["schemas"]["SettlementDetail"];
export type SettlementPage = { content: SettlementSummary[]; page: number; size: number; totalElements: number; totalPages: number };

export const SELLER_SETTLEMENT_PAGE_SIZE = 20;

/** 판매자 정산 목록(기간 시작 최신순). ?page= 가 진실이고 이전 페이지는 placeholder 로만 쓴다. */
export function useSellerSettlements(page: number) {
  const query = { page, size: SELLER_SETTLEMENT_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.sellerSettlements.list(query),
    queryFn: async () => unwrap<SettlementPage>(await api.GET("/api/v1/seller/settlements", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 판매자 정산 상세. 404 SETTLEMENT_NOT_FOUND 는 호출 쪽이 없음 페이지로 보낸다. */
export function useSellerSettlement(settlementId: number) {
  return useQuery({
    queryKey: queryKeys.sellerSettlements.detail(settlementId),
    queryFn: async () => unwrap<SettlementDetail>(await api.GET("/api/v1/seller/settlements/{settlementId}", { params: { path: { settlementId } } })),
    enabled: Number.isSafeInteger(settlementId) && settlementId > 0,
    staleTime: 0,
  });
}
