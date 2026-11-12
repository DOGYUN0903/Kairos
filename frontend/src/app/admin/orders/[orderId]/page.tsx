"use client";

import { useParams } from "next/navigation";
import { AdminOrderDetailPage } from "@/features/admin/orders/components/AdminOrderDetailPage";

export default function AdminOrderPage() {
  const { orderId } = useParams<{ orderId: string }>();
  return <AdminOrderDetailPage orderId={Number(orderId)} />;
}
