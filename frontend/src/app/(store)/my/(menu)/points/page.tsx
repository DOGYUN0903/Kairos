"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { PointsPage } from "@/features/points/components/PointsPage";

export default function Page() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <PointsPage />
    </Suspense>
  );
}
