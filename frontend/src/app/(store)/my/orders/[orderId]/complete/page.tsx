"use client";

import { notFound, useParams } from "next/navigation";
import { Suspense } from "react";
import { parseId } from "@/lib/ids";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { CompletePage } from "@/features/payment/components/CompletePage";

export default function Page() {
  const orderId = parseId(useParams<{ orderId: string }>().orderId);
  if (orderId === null) notFound();
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <CompletePage orderId={orderId} />
      </RequireAuth>
    </Suspense>
  );
}
