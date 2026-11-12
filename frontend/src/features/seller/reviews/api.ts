"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import type { ReviewResult } from "@/features/reviews/api";
import { queryKeys } from "@/lib/query-keys";

export type SellerReviewPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: ReviewResult[] };

export const SELLER_REVIEW_PAGE_SIZE = 20;

export function useSellerReviews(page: number) {
  const query = { page, size: SELLER_REVIEW_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.sellerReviews.list(query),
    queryFn: async () => unwrap<SellerReviewPage>(await api.GET("/api/v1/seller/reviews", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 답글 등록·수정(덮어쓰기, 키 없음). 실패는 던진다. 성공이든 실패든 목록을 다시 읽게 한다. */
export function useReplyReview() {
  const qc = useQueryClient();
  return async (reviewId: number, content: string): Promise<void> => {
    try {
      unwrap<unknown>(await api.PATCH("/api/v1/seller/reviews/{reviewId}/reply", { params: { path: { reviewId } }, body: { content } }));
    } finally {
      await qc.invalidateQueries({ queryKey: queryKeys.sellerReviews.lists });
    }
  };
}
