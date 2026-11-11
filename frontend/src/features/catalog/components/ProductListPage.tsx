"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useMemo } from "react";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { useCategories, useProducts } from "@/features/catalog/api";
import { findCategoryPath } from "@/features/catalog/category-path";
import { FilterPanel } from "@/features/catalog/components/FilterPanel";
import { ProductGrid, ProductGridSkeleton } from "@/features/catalog/components/ProductGrid";
import { SortBar } from "@/features/catalog/components/SortBar";
import {
  hasActiveFilter,
  parseProductListParams,
  toSearchString,
  withFilters,
  type ProductListParams,
} from "@/features/catalog/search-params";

type Props = {
  /** 필터·정렬·페이지를 URL 로 올릴 경로. 브랜드 페이지는 자기 경로를 넘긴다. */
  basePath?: string;
  /** 브랜드 페이지: 이 브랜드로 고정(URL 의 brandId 는 무시). */
  fixedBrandId?: number;
  /** 제목 앞부분을 덮어쓴다(브랜드명 등). */
  heading?: string;
};

export function ProductListPage({ basePath = "/products", fixedBrandId, heading }: Props) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const urlParams = useMemo(() => parseProductListParams(new URLSearchParams(searchParams.toString())), [searchParams]);
  const params: ProductListParams = fixedBrandId !== undefined ? { ...urlParams, brandId: fixedBrandId } : urlParams;

  const categories = useCategories();
  const products = useProducts(params);

  // 브랜드가 고정된 화면에서는 URL 에 brandId 를 싣지 않는다.
  const hrefFor = (next: ProductListParams) => {
    const qs = toSearchString(fixedBrandId !== undefined ? { ...next, brandId: undefined } : next);
    return qs ? `${basePath}?${qs}` : basePath;
  };
  const go = (next: ProductListParams) => router.push(hrefFor(next));
  const change = (c: Partial<ProductListParams>) => go(withFilters(params, c));
  const filtered = hasActiveFilter(params, fixedBrandId);

  const tree = categories.data ?? [];
  const path = params.categoryId !== undefined ? findCategoryPath(tree, params.categoryId) : [];
  const title =
    params.keyword !== undefined
      ? `"${params.keyword}" 검색 결과`
      : path.length > 0
        ? path.map((c) => c.name).join(" > ")
        : (heading ?? "전체 상품");

  const page = products.data;
  const items = page?.content ?? [];

  return (
    <div className="container">
      <section aria-label="핫딜 안내" className="mt-6 rounded bg-surface px-4 py-3 text-sm text-text-2">
        <span className="mr-2 font-bold text-hot">핫딜</span>핫딜 기능은 준비 중입니다.
      </section>
      <div className="mt-8 grid grid-cols-[220px_1fr] gap-10">
        <FilterPanel
          categories={tree}
          params={params}
          {...(fixedBrandId !== undefined ? { fixedBrandId } : {})}
          hasFilter={filtered}
          categoryHref={(categoryId) => hrefFor(withFilters(params, { categoryId }))}
          onChange={change}
          onReset={() =>
            go({ sort: params.sort, page: 0, size: params.size, ...(fixedBrandId !== undefined ? { brandId: fixedBrandId } : {}) })
          }
        />
        <section aria-labelledby="list-title" aria-busy={products.isFetching}>
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-3">
            <h1 id="list-title" className="text-xl font-bold">
              {title}
              {page && <span className="ml-2 text-[13px] font-normal text-text-2">총 {page.totalElements.toLocaleString("ko-KR")}개</span>}
            </h1>
            <SortBar sort={params.sort} onChange={(sort) => change({ sort })} />
          </div>

          {products.isPending ? (
            <ProductGridSkeleton count={8} />
          ) : products.isError ? (
            <ErrorState error={products.error} onRetry={() => void products.refetch()} />
          ) : items.length === 0 ? (
            filtered ? (
              <EmptyState
                message="조건에 맞는 상품이 없습니다."
                action={{ label: "필터 초기화", onClick: () => go({ sort: params.sort, page: 0, size: params.size, ...(fixedBrandId !== undefined ? { brandId: fixedBrandId } : {}) }) }}
              />
            ) : (
              <EmptyState message="등록된 상품이 없습니다." />
            )
          ) : (
            <>
              <ProductGrid products={items} />
              <Pagination page={page?.page ?? 0} totalPages={page?.totalPages ?? 0} onChange={(p) => go({ ...params, page: p })} />
            </>
          )}
        </section>
      </div>
    </div>
  );
}
