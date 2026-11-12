"use client";

import { useAdminCancelItem } from "@/features/admin/orders/api";
import type { OrderItemResult } from "@/features/orders/api";
import { ItemCancelDialog } from "@/features/orders/components/ItemCancelDialog";
import { NoDeductionNotice } from "@/features/orders/components/NoDeductionNotice";

/** 관리자 항목 취소 창. item 이 있을 때만 열린다(닫으면 내용이 언마운트되어 사유가 새로 시작한다). */
export function AdminCancelDialog({ orderId, item, onClose }: { orderId: number; item: OrderItemResult | null; onClose: () => void }) {
  if (!item) return null;
  return <CancelBody key={item.orderItemId} orderId={orderId} item={item} onClose={onClose} />;
}

function CancelBody({ orderId, item, onClose }: { orderId: number; item: OrderItemResult; onClose: () => void }) {
  const cancel = useAdminCancelItem(orderId);
  return (
    <ItemCancelDialog
      item={item}
      cancel={(reason, key) => cancel(item.orderItemId, reason, key)}
      notice={<NoDeductionNotice />}
      submitTone="danger"
      onClose={onClose}
    />
  );
}
