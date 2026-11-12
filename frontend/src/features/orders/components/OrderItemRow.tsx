import type { ReactNode } from "react";
import Link from "next/link";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import type { OrderItemResult } from "@/features/orders/api";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { cn } from "@/lib/utils";

/** 주문 항목 한 줄. 표시 전용이다: 버튼은 action 슬롯으로 받는다. 금액은 서버 값 그대로다. */
export function OrderItemRow({ item, action }: { item: OrderItemResult; action?: ReactNode }) {
  const canceled = item.status === "CANCELED";
  return (
    <div className="grid grid-cols-[72px_minmax(0,1fr)_150px_110px_72px] items-center gap-4 border-b border-border px-5 py-4 last:border-b-0">
      <OrderThumb url={item.thumbnailUrl} size={72} className={cn(canceled && "opacity-50")} />
      <div className={cn("min-w-0", canceled && "opacity-50")}>
        <div className="text-xs text-text-2">{item.brandName}</div>
        <Link className="block truncate font-medium hover:underline" href={`/products/${item.productId}`}>
          {item.productName}
        </Link>
        <div className="text-[13px] text-text-2">
          {item.optionDisplayName} · 수량 {item.quantity}개
        </div>
      </div>
      <div className="flex flex-col items-start gap-1">
        <StatusChip kind="orderItem" status={item.status} />
        {item.status === "CANCEL_REQUESTED" && <span className="text-xs text-text-2">취소를 확인하고 있습니다</span>}
        {canceled && item.refundAmount > 0 && (
          <span className="text-xs">
            환불 <Money value={item.refundAmount} />
          </span>
        )}
      </div>
      <div className={cn("text-right font-bold", canceled && "opacity-50")}>
        <Money value={item.lineAmount} />
      </div>
      <div className="flex justify-end">{action}</div>
    </div>
  );
}
