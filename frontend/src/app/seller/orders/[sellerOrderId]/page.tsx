"use client";

import { useParams } from "next/navigation";
import { SellerOrderDetailPage } from "@/features/seller/orders/components/SellerOrderDetailPage";

export default function SellerOrderPage() {
  const { sellerOrderId } = useParams<{ sellerOrderId: string }>();
  return <SellerOrderDetailPage sellerOrderId={Number(sellerOrderId)} />;
}
