"use client";

import { useQuery } from "@tanstack/react-query";
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
