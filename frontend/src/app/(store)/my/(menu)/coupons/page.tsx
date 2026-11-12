"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { CouponsPage } from "@/features/coupons/components/CouponsPage";

export default function Page() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <CouponsPage />
    </Suspense>
  );
}
