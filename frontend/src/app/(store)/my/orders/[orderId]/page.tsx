"use client";

import { notFound, useParams } from "next/navigation";
import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { OrderDetailPage } from "@/features/orders/components/OrderDetailPage";

export default function Page() {
  const raw = useParams<{ orderId: string }>().orderId;
  if (!/^[1-9]\d{0,15}$/.test(raw)) notFound();
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <OrderDetailPage orderId={Number(raw)} />
      </RequireAuth>
    </Suspense>
  );
}
