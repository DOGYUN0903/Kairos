"use client";

import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { components } from "@/api/schema";
import type { CouponPolicyResult, CreateCouponPolicyRequest } from "@/features/coupon-policies/policy-status";
import type { SellerProductSummary } from "@/features/seller/products/api";
import { queryKeys } from "@/lib/query-keys";

export type SellerPolicyPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: CouponPolicyResult[] };
export type SellerProductPage = Omit<components["schemas"]["PageResponse"], "content"> & { content: SellerProductSummary[] };

export const POLICY_PAGE_SIZE = 20;
export const PICKER_SIZE = 100;

export function useSellerPolicies(page: number) {
  const query = { page, size: POLICY_PAGE_SIZE };
  return useQuery({
    queryKey: queryKeys.sellerCoupons.list(query),
    queryFn: async () => unwrap<SellerPolicyPage>(await api.GET("/api/v1/seller/coupons/policies", { params: { query } })),
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

/** 상품 하나 쿠폰의 선택 상자용: 판매 중인 내 상품(한 번에 최대 100개). */
export function useOnSaleProductsForPicker() {
  const query = { status: "ON_SALE" as const, page: 0, size: PICKER_SIZE };
  return useQuery({
    queryKey: queryKeys.seller.products(query),
    queryFn: async () => unwrap<SellerProductPage>(await api.GET("/api/v1/seller/products", { params: { query } })),
  });
}

/** 정책 등록. 실패는 던진다(useIdempotentSubmit 이 키를 정하고 onError 로 넘긴다). 서버가 답했거나 성공이면 목록을 다시 읽게 한다. */
export function useCreateSellerPolicy() {
  const qc = useQueryClient();
  return async (body: CreateCouponPolicyRequest, key: string): Promise<void> => {
    unwrap<unknown>(await api.POST("/api/v1/seller/coupons/policies", { body, params: { header: { [IDEMPOTENCY_HEADER]: key } } }));
    await qc.invalidateQueries({ queryKey: queryKeys.sellerCoupons.lists });
  };
}

/** 정책 종료. 키 없음(이미 종료여도 200). 성공이든 실패든 목록을 다시 읽는다. */
export function useEndSellerPolicy() {
  const qc = useQueryClient();
  return async (policyId: number): Promise<void> => {
    try {
      // 본문이 없는 응답이다. 실패는 미들웨어가 ApiError 로 던진다.
      await api.PATCH("/api/v1/seller/coupons/policies/{policyId}/end", { params: { path: { policyId } } });
    } finally {
      await qc.invalidateQueries({ queryKey: queryKeys.sellerCoupons.lists });
    }
  };
}
