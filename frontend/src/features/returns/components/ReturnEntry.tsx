"use client";

import { useState } from "react";
import type { OrderItemResult, SellerOrderResult } from "@/features/orders/api";
import { ReturnRequestDialog } from "@/features/returns/components/ReturnRequestDialog";
import { returnEntry } from "@/features/returns/return-actions";
import { formatDateTimeMinute } from "@/lib/datetime";

function entryOf(sellerOrder: Pick<SellerOrderResult, "status" | "returnableUntil">, item: OrderItemResult, nowMs: number) {
  return returnEntry(
    { sellerOrderStatus: sellerOrder.status, itemStatus: item.status, returnableUntil: sellerOrder.returnableUntil, returnStatus: item.returnRequest?.status },
    nowMs,
  );
}

/** 항목 줄 오른쪽 버튼: 반품 요청(창). 구매자 주문 상세에서만 쓴다. */
export function ReturnEntryAction({ orderId, sellerOrder, item, nowMs }: { orderId: number; sellerOrder: SellerOrderResult; item: OrderItemResult; nowMs: number }) {
  const [open, setOpen] = useState(false);
  if (entryOf(sellerOrder, item, nowMs).kind !== "request") return null;
  return (
    <>
      <button type="button" className="btn btn-sm whitespace-nowrap" onClick={() => setOpen(true)}>
        반품 요청
      </button>
      {open && <ReturnRequestDialog orderId={orderId} item={item} onClose={() => setOpen(false)} />}
    </>
  );
}

/** 항목 상태 아래 문구: 기한, 기한 경과, 거절·중단 안내(반품 상세 링크). */
export function ReturnEntryNote({ sellerOrder, item, nowMs }: { sellerOrder: SellerOrderResult; item: OrderItemResult; nowMs: number }) {
  const entry = entryOf(sellerOrder, item, nowMs);
  if (entry.kind === "request") return <span className="text-xs text-text-2">{formatDateTimeMinute(entry.until)}까지 반품 가능</span>;
  if (entry.kind === "expired") return <span className="text-xs text-text-2">반품 가능 기간이 지났습니다</span>;
  // 반품 상세 링크는 ReturnStatusLine 이 그린다(거절·중단된 반품도 요약이 있다).
  if (entry.kind === "blocked") return <span className="text-xs text-text-2">{entry.text}</span>;
  return null;
}
