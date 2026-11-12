"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { MyReturnsPage } from "@/features/returns/components/MyReturnsPage";

export default function Page() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <MyReturnsPage />
    </Suspense>
  );
}
