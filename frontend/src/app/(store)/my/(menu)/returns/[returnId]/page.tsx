"use client";

import { notFound, useParams } from "next/navigation";
import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { MyReturnDetailPage } from "@/features/returns/components/MyReturnDetailPage";
import { parseId } from "@/lib/ids";

export default function Page() {
  const returnId = parseId(useParams<{ returnId: string }>().returnId);
  if (returnId === null) notFound();
  return (
    <Suspense fallback={<PageSkeleton />}>
      <MyReturnDetailPage returnId={returnId} />
    </Suspense>
  );
}
