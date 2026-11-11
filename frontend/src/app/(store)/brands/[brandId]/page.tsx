"use client";

import { notFound, useParams } from "next/navigation";
import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { BrandHeader } from "@/features/catalog/components/BrandHeader";
import { ProductListPage } from "@/features/catalog/components/ProductListPage";

export default function BrandPage() {
  const raw = useParams<{ brandId: string }>().brandId;
  if (!/^[1-9]\d{0,15}$/.test(raw)) notFound();
  const brandId = Number(raw);
  return (
    <>
      <BrandHeader brandId={brandId} />
      <Suspense fallback={<PageSkeleton />}>
        <ProductListPage basePath={`/brands/${brandId}`} fixedBrandId={brandId} heading="브랜드 상품" />
      </Suspense>
    </>
  );
}
