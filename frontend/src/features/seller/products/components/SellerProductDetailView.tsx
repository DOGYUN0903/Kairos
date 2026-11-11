"use client";

import Link from "next/link";
import { ApiError } from "@/api/client";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { useSellerCapabilities } from "@/features/seller/gate/useSellerCapabilities";
import { useSellerProduct, useUpdateSku } from "@/features/seller/products/api";
import { ChangeRequestCard } from "@/features/seller/products/components/ChangeRequestCard";
import { ProductStatusActions } from "@/features/seller/products/components/ProductStatusActions";
import { ProductSummaryCard } from "@/features/seller/products/components/ProductSummaryCard";
import { SkuEditTable } from "@/features/seller/products/components/SkuEditTable";

export function SellerProductDetailView({ productId }: { productId: number }) {
  const valid = Number.isSafeInteger(productId) && productId > 0;
  const { canManage } = useSellerCapabilities();
  const detail = useSellerProduct(productId, valid);
  const updateSku = useUpdateSku(productId);

  if (!valid || (detail.error instanceof ApiError && detail.error.code === "PRODUCT_NOT_FOUND")) {
    return <EmptyState message="상품을 찾을 수 없습니다" action={{ label: "상품 목록으로", href: "/seller/products" }} />;
  }
  if (detail.isPending) return <PageSkeleton />;
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => void detail.refetch()} />;
  const p = detail.data;
  const ended = p.status === "ENDED";

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-3">
        <Link href="/seller/products" className="text-sm text-text-2 hover:underline">
          ← 상품 목록
        </Link>
        <ProductStatusActions product={p} canManage={canManage} />
      </div>
      {!canManage && <div className="notice notice-warn">정지 중에는 조회와 일시 중지·종료만 할 수 있습니다.</div>}
      <ProductSummaryCard product={p} canManage={canManage} />
      <section className="card" aria-labelledby="skus-h">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <h2 id="skus-h" className="text-[15px] font-bold">
            SKU 재고·판매
          </h2>
          <span className="text-xs text-text-2">추가 금액·재고·판매 여부는 저장하면 바로 반영됩니다.</span>
        </div>
        <SkuEditTable
          skus={p.skus}
          disabled={!canManage || ended}
          onSave={(skuId, body) => updateSku.mutateAsync({ skuId, body })}
        />
      </section>
      <ChangeRequestCard product={p} canManage={canManage} />
    </div>
  );
}
