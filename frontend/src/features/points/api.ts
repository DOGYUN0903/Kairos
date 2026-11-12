"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { useAuth } from "@/auth/useAuth";
import { queryKeys } from "@/lib/query-keys";

export type PointSummary = components["schemas"]["PointSummary"];

/** 내 포인트 잔액과 30일 내 만료 예정. */
export function usePointSummary() {
  const { status } = useAuth();
  return useQuery({
    queryKey: queryKeys.points.summary,
    queryFn: async () => unwrap<PointSummary>(await api.GET("/api/v1/points/me")),
    enabled: status === "authenticated",
  });
}

export type PointTransaction = components["schemas"]["PointTransactionResult"];
export type PointTransactionPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: PointTransaction[] };

export const POINT_PAGE_SIZE = 20;

/** 내 포인트 내역(최신순). page 는 0부터. */
export function usePointTransactions(page: number) {
  const { status } = useAuth();
  const query = { page, size: POINT_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.points.transactions(query),
    queryFn: async () => unwrap<PointTransactionPage>(await api.GET("/api/v1/points/me/transactions", { params: { query } })),
    placeholderData: keepPreviousData,
    enabled: status === "authenticated",
  });
}
