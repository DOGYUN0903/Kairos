"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { OrderListPage } from "@/features/orders/components/OrderListPage";

export default function Page() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <OrderListPage />
    </Suspense>
  );
}
