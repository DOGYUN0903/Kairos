"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { useAuth } from "@/auth/useAuth";
import { isOnSale } from "@/features/likes/status";
import { queryKeys } from "@/lib/query-keys";

export { isOnSale };
export type LikedProduct = components["schemas"]["LikedProduct"];
export type LikedProductPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: LikedProduct[] };

export const LIKES_PAGE_SIZE = 12;

/** 내 좋아요 목록(최근 좋아요 순은 서버 정렬 그대로). page 는 0부터. size 를 줄이면 개수만 읽는 데 쓸 수 있다. */
export function useMyLikes(page: number, size: number = LIKES_PAGE_SIZE) {
  const { status } = useAuth();
  const query = { page, size };
  return useQuery({
    queryKey: queryKeys.likes.list(query),
    queryFn: async () => unwrap<LikedProductPage>(await api.GET("/api/v1/products/me/likes", { params: { query } })),
    placeholderData: keepPreviousData,
    enabled: status === "authenticated",
  });
}
