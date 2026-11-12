"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { CouponPolicyResult, CreateCouponPolicyRequest } from "@/features/coupon-policies/policy-status";
import type { SellerPolicyPage } from "@/features/seller/coupons/api";
import { queryKeys } from "@/lib/query-keys";

export const ADMIN_POLICY_PAGE_SIZE = 20;
export type AdminPolicyPage = Omit<SellerPolicyPage, "content"> & { content: CouponPolicyResult[] };

export function useAdminPolicies(page: number) {
  const query = { page, size: ADMIN_POLICY_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.adminCoupons.list(query),
    queryFn: async () => unwrap<AdminPolicyPage>(await api.GET("/api/v1/admin/coupons/policies", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 정책 등록. 실패는 던진다(useIdempotentSubmit 이 키를 정한다). */
export function useCreateAdminPolicy() {
  const qc = useQueryClient();
  return async (body: CreateCouponPolicyRequest, key: string): Promise<void> => {
    unwrap<unknown>(await api.POST("/api/v1/admin/coupons/policies", { body, params: { header: { [IDEMPOTENCY_HEADER]: key } } }));
    await qc.invalidateQueries({ queryKey: queryKeys.adminCoupons.lists });
  };
}

/** 정책 종료. 키 없음. 성공이든 실패든 목록을 다시 읽는다. */
export function useEndAdminPolicy() {
  const qc = useQueryClient();
  return async (policyId: number): Promise<void> => {
    try {
      await api.PATCH("/api/v1/admin/coupons/policies/{policyId}/end", { params: { path: { policyId } } });
    } finally {
      await qc.invalidateQueries({ queryKey: queryKeys.adminCoupons.lists });
    }
  };
}
