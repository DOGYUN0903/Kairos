"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import type { PointTransaction } from "@/features/points/api";
import { queryKeys } from "@/lib/query-keys";

export type AdminMemberPoints = Omit<components["schemas"]["AdminMemberPoints"], "transactions"> & {
  transactions: Omit<components["schemas"]["PageResponse"], "content"> & { content: PointTransaction[] };
};

export const ADMIN_POINT_PAGE_SIZE = 20;

/** 회원 한 명의 포인트. memberId 가 null 이면 조회하지 않는다. 없는 회원도 잔액 0·빈 내역으로 온다(404 아님). */
export function useAdminMemberPoints(memberId: number | null, page: number) {
  const query = { page };
  return useQuery({
    queryKey: queryKeys.adminMemberPoints(memberId ?? 0, query),
    queryFn: async () =>
      unwrap<AdminMemberPoints>(await api.GET("/api/v1/admin/members/{memberId}/points", { params: { path: { memberId: memberId as number }, query } })),
    placeholderData: keepPreviousData,
    enabled: memberId !== null,
  });
}
