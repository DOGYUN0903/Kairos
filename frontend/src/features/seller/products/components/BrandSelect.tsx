"use client";

import Link from "next/link";
import { forwardRef, type SelectHTMLAttributes } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { useBrands } from "@/features/seller/brands/api";

type Props = SelectHTMLAttributes<HTMLSelectElement> & { error?: string };

/** APPROVED 브랜드만 고를 수 있다. 없으면 브랜드 신청 안내. register("brandId", { valueAsNumber: true }) 와 함께 쓴다. */
export const BrandSelect = forwardRef<HTMLSelectElement, Props>(function BrandSelect({ error, id = "p-brand", ...rest }, ref) {
  const q = useBrands();
  const approved = (q.data ?? []).filter((b) => b.status === "APPROVED");

  return (
    <div className="field">
      <label htmlFor={id}>
        브랜드<span className="text-danger" aria-hidden>*</span>
      </label>
      {q.isPending ? (
        <Skeleton className="h-11 w-full" aria-label="브랜드 불러오는 중" />
      ) : q.isError ? (
        <p role="alert" className="text-sm text-danger">
          브랜드를 불러오지 못했습니다.{" "}
          <button type="button" className="underline" onClick={() => void q.refetch()}>
            다시 시도
          </button>
        </p>
      ) : approved.length === 0 ? (
        <p className="notice">
          승인된 브랜드가 없습니다.{" "}
          <Link href="/seller/brands?new=1" className="font-medium text-navy underline">
            브랜드 신청
          </Link>
        </p>
      ) : (
        <select
          ref={ref}
          id={id}
          className="input"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-err` : `${id}-hint`}
          {...rest}
        >
          <option value="">브랜드 선택</option>
          {approved.map((b) => (
            <option key={b.brandId} value={b.brandId}>
              {b.name}
            </option>
          ))}
        </select>
      )}
      {error ? (
        <span id={`${id}-err`} className="field-error">
          {error}
        </span>
      ) : (
        <span id={`${id}-hint`} className="hint">
          승인된 브랜드만 선택할 수 있습니다.
        </span>
      )}
    </div>
  );
});
