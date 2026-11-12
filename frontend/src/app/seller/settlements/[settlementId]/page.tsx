"use client";

import { notFound, useParams } from "next/navigation";
import { SellerSettlementDetailPage } from "@/features/seller/settlements/components/SellerSettlementDetailPage";
import { parseId } from "@/lib/ids";

export default function SellerSettlementPage() {
  const settlementId = parseId(useParams<{ settlementId: string }>().settlementId);
  if (settlementId === null) notFound();
  return <SellerSettlementDetailPage settlementId={settlementId} />;
}
