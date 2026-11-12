"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import { queryKeys } from "@/lib/query-keys";

export type ReviewResult = components["schemas"]["ReviewResult"];
export type ReviewImage = components["schemas"]["ReviewImage"];
export type OrderItemReview = components["schemas"]["OrderItemReview"];
export type WriteReviewRequest = components["schemas"]["WriteReviewRequest"];
export type WriteReviewResponse = components["schemas"]["WriteReviewResponse"];
export type ReviewPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: ReviewResult[] };

export const PRODUCT_REVIEW_PAGE_SIZE = 5;
export const MY_REVIEW_PAGE_SIZE = 10;

/** 상품 후기(인증 불필요). 페이지를 넘기는 동안 이전 페이지를 placeholder 로 둔다. */
export function useProductReviews(productId: number, page: number) {
  const query = { page, size: PRODUCT_REVIEW_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.reviews.product(productId, page),
    queryFn: async () => unwrap<ReviewPage>(await api.GET("/api/v1/products/{productId}/reviews", { params: { path: { productId }, query } })),
    enabled: Number.isSafeInteger(productId) && productId > 0,
    placeholderData: keepPreviousData,
  });
}

export function useMyReviews(page: number) {
  const query = { page, size: MY_REVIEW_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.reviews.mine(query),
    queryFn: async () => unwrap<ReviewPage>(await api.GET("/api/v1/reviews/me", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 실패는 던진다(useIdempotentSubmit 이 잡는다). */
export async function writeReview(orderId: number, orderItemId: number, body: WriteReviewRequest, key: string): Promise<WriteReviewResponse> {
  return unwrap<WriteReviewResponse>(
    await api.POST("/api/v1/orders/{orderId}/items/{orderItemId}/reviews", {
      params: { path: { orderId, orderItemId }, header: { [IDEMPOTENCY_HEADER]: key } },
      body,
    }),
  );
}

export async function editReview(reviewId: number, body: WriteReviewRequest): Promise<void> {
  await api.PATCH("/api/v1/reviews/{reviewId}", { params: { path: { reviewId } }, body });
}

export async function deleteReview(reviewId: number): Promise<void> {
  await api.DELETE("/api/v1/reviews/{reviewId}", { params: { path: { reviewId } } });
}
