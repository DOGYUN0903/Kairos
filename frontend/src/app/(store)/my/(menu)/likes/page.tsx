"use client";

import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { MyLikesPage } from "@/features/likes/components/MyLikesPage";

export default function Page() {
  return (
    <Suspense fallback={<PageSkeleton />}>
      <MyLikesPage />
    </Suspense>
  );
}
