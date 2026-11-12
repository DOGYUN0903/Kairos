"use client";

import { ChevronRight, Star } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/api/client";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Money } from "@/components/common/Money";
import { Skeleton } from "@/components/ui/skeleton";
import { useCategories, useProduct, type ProductDetail } from "@/features/catalog/api";
import { findCategoryPath } from "@/features/catalog/category-path";
import { AddToCartButtons } from "@/features/cart/components/AddToCartButtons";
import { ProductCouponSection } from "@/features/coupons/components/ProductCouponSection";
import { LikeButton } from "@/features/catalog/components/LikeButton";
import { OptionSelector } from "@/features/catalog/components/OptionSelector";
import { ProductGallery } from "@/features/catalog/components/ProductGallery";
import { QuantityInput } from "@/features/catalog/components/QuantityInput";
import { findSku, selectionLabel, selectionTotal, type Selection } from "@/features/catalog/sku-match";

const count = new Intl.NumberFormat("ko-KR");

function DetailSkeleton() {
  return (
    <div aria-busy="true" aria-label="불러오는 중" className="container grid grid-cols-[minmax(0,1fr)_440px] gap-14 py-10">
      <Skeleton className="aspect-square w-full" />
      <div className="flex flex-col gap-4">
        <Skeleton className="h-5 w-24" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-16 w-48" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    </div>
  );
}

function Breadcrumb({ categoryId }: { categoryId: number }) {
  const { data } = useCategories();
  const path = data ? findCategoryPath(data, categoryId) : [];
  const last = path.length - 1;
  return (
    <nav aria-label="현재 위치" className="my-5 flex flex-wrap gap-2 text-xs text-text-2">
      <Link href="/" className="hover:text-navy">
        홈
      </Link>
      {path.map((c, i) => (
        <span key={c.categoryId} className="flex gap-2">
          <span aria-hidden>/</span>
          <Link href={`/products?categoryId=${c.categoryId}`} aria-current={i === last ? "page" : undefined} className="hover:text-navy">
            {c.name}
          </Link>
        </span>
      ))}
    </nav>
  );
}

function PurchasePanel({ product }: { product: ProductDetail }) {
  const [selected, setSelected] = useState<Selection>({});
  const [qty, setQty] = useState(1);
  const sku = findSku(product.skus, selected, product.options);
  const label = selectionLabel(product.options, selected);
  const skuSoldOut = sku?.soldOut === true;

  const select = (optionId: number, valueId: number | null) =>
    setSelected((prev) => {
      const next = { ...prev };
      if (valueId === null) delete next[optionId];
      else next[optionId] = valueId;
      return next;
    });

  return (
    <section aria-labelledby="pname" className="sticky top-24 self-start">
      <Link href={`/brands/${product.brandId}`} className="inline-flex items-center gap-1 text-sm font-bold">
        {product.brandName}
        <ChevronRight className="h-4 w-4" aria-hidden />
      </Link>
      <h1 id="pname" className="mt-2 text-[22px] font-medium leading-snug">
        {product.name}
      </h1>
      <div className="mt-2 flex items-center gap-3 text-xs text-text-2">
        <span className="flex items-center gap-1">
          <Star className="h-3.5 w-3.5" aria-hidden />
          {product.reviewCount > 0 ? `${product.averageRating.toFixed(1)} · 후기 ${count.format(product.reviewCount)}개` : "아직 후기가 없습니다"}
        </span>
      </div>

      <div className="mt-4 border-b border-border pb-5">
        {product.discountRate > 0 && (
          <div className="text-sm text-text-3">
            정가 <Money value={product.originalPrice} className="line-through" />
          </div>
        )}
        <div className="mt-0.5 flex items-baseline gap-2">
          {product.discountRate > 0 && <span className="text-[22px] font-bold text-discount">{product.discountRate}%</span>}
          <Money value={product.salePrice} className="text-2xl font-bold" />
        </div>
      </div>

      <ProductCouponSection productId={product.productId} />

      {product.soldOut && (
        <p role="status" className="notice notice-warn mt-4">
          품절된 상품입니다.
        </p>
      )}

      <OptionSelector options={product.options} skus={product.skus} selected={selected} soldOut={product.soldOut} onSelect={select} />

      <div aria-live="polite">
        {sku && !skuSoldOut && !product.soldOut && (
          <div className="mt-4 rounded bg-surface p-3.5">
            <div className="flex items-start justify-between">
              <div>
                <div className="font-medium">{label || product.name}</div>
                <div className="text-xs text-text-2">
                  <Money value={product.salePrice} />
                  {sku.extraPrice > 0 && (
                    <>
                      {" "}
                      + 옵션 <Money value={sku.extraPrice} />
                    </>
                  )}
                </div>
              </div>
              <QuantityInput value={qty} onChange={setQty} />
            </div>
          </div>
        )}
        {skuSoldOut && <p className="mt-4 text-sm text-danger">선택하신 옵션은 품절입니다.</p>}
      </div>

      <div className="mt-5 flex items-baseline justify-between">
        <span className="text-text-2">
          총 상품 금액 {sku && !skuSoldOut && <span className="text-xs">({qty}개)</span>}
        </span>
        {sku && !skuSoldOut && !product.soldOut ? (
          <Money value={selectionTotal(product.salePrice, sku, qty)} className="text-[22px] font-bold" />
        ) : (
          <span className="text-text-2">{product.soldOut ? "품절" : skuSoldOut ? "선택 불가" : "옵션을 선택하세요"}</span>
        )}
      </div>

      <div className="mt-4 grid grid-cols-[52px_1fr] items-center gap-2">
        <LikeButton productId={product.productId} liked={product.liked} likeCount={product.likeCount} />
        <AddToCartButtons skuId={sku?.skuId ?? null} quantity={qty} disabled={product.soldOut || skuSoldOut} />
      </div>

      <dl className="mt-5 grid grid-cols-[72px_1fr] gap-y-1.5 text-xs text-text-2">
        <dt className="font-medium">판매자</dt>
        <dd>{product.sellerName}</dd>
        <dt className="font-medium">상품 번호</dt>
        <dd>{product.productId}</dd>
      </dl>
    </section>
  );
}

export function ProductDetailView({ productId }: { productId: number }) {
  const query = useProduct(productId);

  if (query.isError && query.error instanceof ApiError && query.error.code === "PRODUCT_NOT_FOUND") notFound();
  if (query.isPending) return <DetailSkeleton />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => void query.refetch()} />;

  const product = query.data;
  return (
    <div className="container">
      <Breadcrumb categoryId={product.categoryId} />
      <div className="grid grid-cols-[minmax(0,1fr)_440px] gap-14">
        <ProductGallery imageUrls={product.imageUrls} name={product.name} />
        {/* 상품이 바뀌면 선택 상태를 새로 시작한다. */}
        <PurchasePanel key={product.productId} product={product} />
      </div>
      <section aria-label="상품 정보" className="mt-[72px] border-t border-border">
        <div className="mx-auto mt-10 max-w-[760px] pb-16">
          <h2 className="text-lg font-bold">상품 설명</h2>
          {product.description.trim() ? (
            <p className="mt-4 whitespace-pre-line leading-relaxed">{product.description}</p>
          ) : (
            <EmptyState message="등록된 상품 설명이 없습니다." />
          )}
        </div>
      </section>
    </div>
  );
}
