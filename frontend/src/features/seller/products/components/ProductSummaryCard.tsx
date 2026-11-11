"use client";

import { useState } from "react";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import type { SellerProductDetail } from "@/features/seller/products/api";
import { PriceDialog } from "@/features/seller/products/components/PriceDialog";
import { formatDate } from "@/lib/datetime";

export function ProductSummaryCard({ product, canManage }: { product: SellerProductDetail; canManage: boolean }) {
  const [priceOpen, setPriceOpen] = useState(false);
  const main = product.images.find((i) => i.isMain) ?? product.images[0];
  const priceEditable = canManage && product.status !== "ENDED";
  const active = product.skus.filter((s) => s.skuStatus === "ACTIVE");
  const stockTotal = active.reduce((sum, s) => sum + s.stock, 0);

  return (
    <section className="card flex gap-5 p-5" aria-labelledby="summary-h">
      {main ? (
        // eslint-disable-next-line @next/next/no-img-element -- 백엔드 절대 URL 그대로(계획: next/image 미사용)
        <img src={main.url} alt="" className="h-32 w-32 flex-none rounded object-cover" />
      ) : (
        <div className="grid h-32 w-32 flex-none place-items-center rounded bg-surface-2 text-xs text-text-3">이미지 없음</div>
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <StatusChip kind="product" status={product.status} />
          {product.soldOut && product.status !== "ENDED" && (
            <span className="rounded-sm border border-border px-1.5 py-0.5 text-[11px] text-text-2">품절</span>
          )}
          <span className="text-xs text-text-2">
            #{product.productId} · {product.brandName}
          </span>
        </div>
        <h1 id="summary-h" className="text-xl font-bold">
          {product.name}
        </h1>
        {product.status === "REJECTED" && product.rejectReason && <p className="text-sm text-danger">반려 사유: {product.rejectReason}</p>}
        <dl className="mt-1 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-text-2">정가</dt>
          <dd>
            <Money value={product.originalPrice} />
          </dd>
          <dt className="text-text-2">판매가</dt>
          <dd className="flex items-center gap-2">
            <Money value={product.salePrice} className="font-bold" />
            {product.discountRate > 0 && <strong className="text-danger">{product.discountRate}%</strong>}
            {priceEditable && (
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => setPriceOpen(true)}>
                판매가 수정
              </button>
            )}
          </dd>
          <dt className="text-text-2">총 재고</dt>
          <dd className="tabular-nums">{stockTotal}</dd>
          <dt className="text-text-2">판매량</dt>
          <dd className="tabular-nums">{product.salesCount}</dd>
          {product.onSaleAt && (
            <>
              <dt className="text-text-2">판매 시작</dt>
              <dd>{formatDate(product.onSaleAt)}</dd>
            </>
          )}
        </dl>
      </div>
      {priceOpen && <PriceDialog product={product} onClose={() => setPriceOpen(false)} />}
    </section>
  );
}
