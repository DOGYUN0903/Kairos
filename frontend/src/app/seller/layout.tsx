"use client";

import { Suspense, type ReactNode } from "react";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { SellerGate } from "@/features/seller/gate/SellerGate";

export default function SellerLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <RequireAuth>
        <SellerGate>{children}</SellerGate>
      </RequireAuth>
    </Suspense>
  );
}
