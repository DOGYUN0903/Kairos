"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import type { CouponStatus } from "@/features/coupons/coupon-params";
import { queryKeys } from "@/lib/query-keys";

export type CouponPolicyResult = components["schemas"]["CouponPolicyResult"];
export type CouponResult = components["schemas"]["CouponResult"];
export type ClaimCouponResponse = components["schemas"]["ClaimCouponResponse"];
type PageMeta = Omit<components["schemas"]["PageResponse"], "content">;
export type CouponPolicyPage = PageMeta & { content: CouponPolicyResult[] };
export type CouponPage = PageMeta & { content: CouponResult[] };

export const CLAIMABLE_PAGE_SIZE = 50;
export const MY_COUPON_PAGE_SIZE = 10;

/** 받을 수 있는 쿠폰 정책. 인증이 필요한 API 라 enabled 가 false 면 부르지 않는다. */
export function useClaimablePolicies(productId: number | undefined, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.coupons.policies(productId),
    queryFn: async () => {
      const query = { size: CLAIMABLE_PAGE_SIZE, ...(productId !== undefined ? { productId } : {}) };
      return unwrap<CouponPolicyPage>(await api.GET("/api/v1/coupons/policies", { params: { query } }));
    },
    enabled,
    staleTime: 0,
  });
}

/** size 를 줄이면 개수(totalElements)만 읽는 데 쓸 수 있다. */
export function useMyCoupons(params: { status: CouponStatus; page: number; size?: number }) {
  const query = { status: params.status, page: params.page, size: params.size ?? MY_COUPON_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.coupons.mine(query),
    queryFn: async () => unwrap<CouponPage>(await api.GET("/api/v1/coupons/me", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 실패는 던진다(useIdempotentAction 이 잡는다). */
export async function claimCoupon(policyId: number, key: string): Promise<ClaimCouponResponse> {
  return unwrap<ClaimCouponResponse>(
    await api.POST("/api/v1/coupons/policies/{policyId}/claim", {
      params: { path: { policyId }, header: { [IDEMPOTENCY_HEADER]: key } },
    }),
  );
}
