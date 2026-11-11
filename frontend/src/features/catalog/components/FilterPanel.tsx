"use client";

import { useState, type FormEvent } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { useBrands } from "@/features/catalog/api";
import { CategoryTree } from "@/features/catalog/components/CategoryTree";
import type { CategoryNode } from "@/features/catalog/category-path";
import type { ProductListParams } from "@/features/catalog/search-params";
import { parseWon } from "@/lib/money";

type Props = {
  categories: CategoryNode[];
  params: ProductListParams;
  /** 브랜드 페이지처럼 브랜드가 고정된 화면이면 브랜드 필터를 숨긴다. */
  fixedBrandId?: number;
  hasFilter: boolean;
  categoryHref: (categoryId: number | undefined) => string;
  onChange: (change: Partial<ProductListParams>) => void;
  onReset: () => void;
};

/** 브랜드 단일 선택(API 가 brandId 하나만 받는다). "전체"는 브랜드 필터 해제. */
function BrandFilter({ brandId, onChange }: { brandId: number | undefined; onChange: (brandId: number | undefined) => void }) {
  const { data, isPending, isError, refetch } = useBrands();
  return (
    <div className="border-t border-border py-4">
      <h2 id="brand-h" className="mb-2 text-[13px] font-bold">
        브랜드
      </h2>
      {isPending ? (
        <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-2">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-5 w-20" />
        </div>
      ) : isError ? (
        <div role="alert" className="text-xs text-text-2">
          브랜드를 불러오지 못했습니다.{" "}
          <button type="button" className="underline" onClick={() => void refetch()}>
            다시 시도
          </button>
        </div>
      ) : (
        <div role="radiogroup" aria-labelledby="brand-h" className="flex max-h-56 flex-col overflow-y-auto">
          {[{ brandId: undefined, name: "전체" }, ...data].map((b) => (
            <label key={b.brandId ?? "all"} className="flex min-h-[36px] cursor-pointer items-center gap-2">
              <input type="radio" name="brandId" checked={b.brandId === brandId} onChange={() => onChange(b.brandId)} />
              {b.name}
            </label>
          ))}
        </div>
      )}
    </div>
  );
}

function PriceForm({ params, onChange }: Pick<Props, "params" | "onChange">) {
  const [min, setMin] = useState(params.minPrice !== undefined ? String(params.minPrice) : "");
  const [max, setMax] = useState(params.maxPrice !== undefined ? String(params.maxPrice) : "");
  const [error, setError] = useState<string | null>(null);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const lo = min.trim() === "" ? undefined : parseWon(min);
    const hi = max.trim() === "" ? undefined : parseWon(max);
    if (lo === null || hi === null) {
      setError("금액은 숫자로 입력하세요.");
      return;
    }
    if (lo !== undefined && hi !== undefined && lo > hi) {
      setError("최소 금액이 최대 금액보다 클 수 없습니다.");
      return;
    }
    setError(null);
    // undefined 를 명시해야 이전 값이 지워진다.
    onChange({ minPrice: lo, maxPrice: hi });
  };

  return (
    <form onSubmit={submit} noValidate>
      <div role="group" aria-labelledby="price-h" className="flex items-center gap-1.5">
        <label className="sr-only" htmlFor="minPrice">
          최소 가격
        </label>
        <input id="minPrice" className="input px-2" inputMode="numeric" placeholder="최소" value={min} onChange={(e) => setMin(e.target.value)} aria-invalid={error ? true : undefined} />
        <span aria-hidden>~</span>
        <label className="sr-only" htmlFor="maxPrice">
          최대 가격
        </label>
        <input id="maxPrice" className="input px-2" inputMode="numeric" placeholder="최대" value={max} onChange={(e) => setMax(e.target.value)} aria-invalid={error ? true : undefined} />
      </div>
      {error && (
        <p role="alert" className="field-error mt-2">
          {error}
        </p>
      )}
      <p className="hint mt-2">판매가 기준 (옵션 추가 금액 제외)</p>
      <button type="submit" className="btn btn-primary btn-block mt-4">
        적용
      </button>
    </form>
  );
}

export function FilterPanel({ categories, params, fixedBrandId, hasFilter, categoryHref, onChange, onReset }: Props) {
  const showBrand = fixedBrandId === undefined;
  return (
    <aside aria-label="필터" className="sticky top-24 self-start">
      <div className="pb-4">
        <h2 className="mb-2 text-[13px] font-bold">카테고리</h2>
        <CategoryTree categories={categories} categoryId={params.categoryId} hrefFor={categoryHref} />
      </div>
      {showBrand && <BrandFilter brandId={params.brandId} onChange={(brandId) => onChange({ brandId })} />}
      <div className="border-t border-border py-4">
        <h2 id="price-h" className="mb-2 text-[13px] font-bold">
          가격대
        </h2>
        {/* 적용된 값이 바뀌면(초기화·뒤로 가기) 입력칸도 다시 맞춘다. */}
        <PriceForm key={`${params.minPrice ?? ""}-${params.maxPrice ?? ""}`} params={params} onChange={onChange} />
        <button type="button" className="btn btn-ghost btn-block mt-2" onClick={onReset} disabled={!hasFilter}>
          초기화
        </button>
      </div>
    </aside>
  );
}
