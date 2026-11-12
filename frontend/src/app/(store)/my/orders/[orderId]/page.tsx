"use client";

import { notFound, useParams } from "next/navigation";
import { Suspense } from "react";
import { parseId } from "@/lib/ids";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { OrderDetailPage } from "@/features/orders/components/OrderDetailPage";

export default function Page() {
  const orderId = parseId(useParams<{ orderId: string }>().orderId);
  if (orderId === null) notFound();
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <OrderDetailPage orderId={orderId} />
      </RequireAuth>
    </Suspense>
  );
}
