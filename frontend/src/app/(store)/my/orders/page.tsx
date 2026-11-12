"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { OrderListPage } from "@/features/orders/components/OrderListPage";

export default function Page() {
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <OrderListPage />
      </RequireAuth>
    </Suspense>
  );
}
