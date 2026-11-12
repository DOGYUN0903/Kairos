"use client";

import { notFound, useParams } from "next/navigation";
import { SellerReturnDetailPage } from "@/features/seller/returns/components/SellerReturnDetailPage";
import { parseId } from "@/lib/ids";

export default function SellerReturnPage() {
  const returnId = parseId(useParams<{ returnId: string }>().returnId);
  if (returnId === null) notFound();
  return <SellerReturnDetailPage returnId={returnId} />;
}
