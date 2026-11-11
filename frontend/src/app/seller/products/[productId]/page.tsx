"use client";

import { useParams } from "next/navigation";
import { SellerProductDetailView } from "@/features/seller/products/components/SellerProductDetailView";

export default function SellerProductDetailPage() {
  const { productId } = useParams<{ productId: string }>();
  return <SellerProductDetailView productId={Number(productId)} />;
}
