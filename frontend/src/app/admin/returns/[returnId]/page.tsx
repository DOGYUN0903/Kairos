"use client";

import { notFound, useParams } from "next/navigation";
import { AdminReturnDetailPage } from "@/features/admin/returns/components/AdminReturnDetailPage";
import { parseId } from "@/lib/ids";

export default function AdminReturnPage() {
  const returnId = parseId(useParams<{ returnId: string }>().returnId);
  if (returnId === null) notFound();
  return <AdminReturnDetailPage returnId={returnId} />;
}
