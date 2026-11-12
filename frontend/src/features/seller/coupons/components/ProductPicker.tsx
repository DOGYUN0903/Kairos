"use client";

import { useFormContext } from "react-hook-form";
import { Skeleton } from "@/components/ui/skeleton";
import type { PolicyFormInput } from "@/features/coupon-policies/schema";
import { PICKER_SIZE, useOnSaleProductsForPicker } from "@/features/seller/coupons/api";

/** "상품 하나" 쿠폰의 대상 상품 선택 상자(판매 중인 내 상품). PolicyFormDialog 의 폼 컨텍스트 안에서만 쓴다. */
export function ProductPicker() {
  const { register, formState } = useFormContext<PolicyFormInput>();
  const q = useOnSaleProductsForPicker();
  const error = formState.errors.targetProductId?.message as string | undefined;
  const products = q.data?.content ?? [];

  return (
    <div className="field mt-1 max-w-[560px]">
      <label htmlFor="pf-product">
        상품 선택<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
      </label>
      {q.isPending ? (
        <Skeleton className="h-11 w-full" aria-label="상품 불러오는 중" />
      ) : q.isError ? (
        <p role="alert" className="text-sm text-danger">
          상품을 불러오지 못했습니다.{" "}
          <button type="button" className="underline" onClick={() => void q.refetch()}>
            다시 시도
          </button>
        </p>
      ) : products.length === 0 ? (
        <p className="notice">판매 중인 상품이 없습니다. 상품이 판매 중일 때 상품 하나 쿠폰을 만들 수 있습니다.</p>
      ) : (
        <select id="pf-product" className="input" aria-invalid={error ? true : undefined} aria-describedby={error ? "pf-product-err" : "pf-product-hint"} {...register("targetProductId")}>
          <option value="">상품 선택</option>
          {products.map((p) => (
            <option key={p.productId} value={String(p.productId)}>
              {p.name} (#{p.productId})
            </option>
          ))}
        </select>
      )}
      {(q.data?.totalElements ?? 0) > PICKER_SIZE && <p className="hint">판매 중인 상품이 많아 최근 100개만 표시합니다.</p>}
      {error ? (
        <span id="pf-product-err" role="alert" className="field-error">
          {error}
        </span>
      ) : (
        <span id="pf-product-hint" className="hint">
          판매 중인 내 상품에서 고릅니다
        </span>
      )}
    </div>
  );
}
