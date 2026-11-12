"use client";

import { notFound, useParams } from "next/navigation";
import { SellerOrderDetailPage } from "@/features/seller/orders/components/SellerOrderDetailPage";
import { parseId } from "@/lib/ids";

export default function SellerOrderPage() {
  const sellerOrderId = parseId(useParams<{ sellerOrderId: string }>().sellerOrderId);
  if (sellerOrderId === null) notFound();
  return <SellerOrderDetailPage sellerOrderId={sellerOrderId} />;
}
