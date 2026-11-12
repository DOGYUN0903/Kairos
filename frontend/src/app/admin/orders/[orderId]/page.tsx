"use client";

import { notFound, useParams } from "next/navigation";
import { AdminOrderDetailPage } from "@/features/admin/orders/components/AdminOrderDetailPage";
import { parseId } from "@/lib/ids";

export default function AdminOrderPage() {
  const orderId = parseId(useParams<{ orderId: string }>().orderId);
  if (orderId === null) notFound();
  return <AdminOrderDetailPage orderId={orderId} />;
}
