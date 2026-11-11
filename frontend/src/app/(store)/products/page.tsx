"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { ProductListPage } from "@/features/catalog/components/ProductListPage";

export default function ProductsPage() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <ProductListPage />
    </Suspense>
  );
}
