"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { MyReviewsPage } from "@/features/reviews/components/MyReviewsPage";

export default function Page() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <MyReviewsPage />
    </Suspense>
  );
}
