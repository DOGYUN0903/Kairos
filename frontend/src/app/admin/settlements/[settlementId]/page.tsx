"use client";

import { notFound, useParams } from "next/navigation";
import { AdminSettlementDetailPage } from "@/features/admin/settlements/components/AdminSettlementDetailPage";
import { parseId } from "@/lib/ids";

export default function AdminSettlementPage() {
  const settlementId = parseId(useParams<{ settlementId: string }>().settlementId);
  if (settlementId === null) notFound();
  return <AdminSettlementDetailPage settlementId={settlementId} />;
}
