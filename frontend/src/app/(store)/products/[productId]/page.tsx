"use client";

import { notFound, useParams } from "next/navigation";
import { ProductDetailView } from "@/features/catalog/components/ProductDetailView";

export default function ProductDetailPage() {
  const raw = useParams<{ productId: string }>().productId;
  if (!/^[1-9]\d{0,15}$/.test(raw)) notFound();
  return <ProductDetailView productId={Number(raw)} />;
}
