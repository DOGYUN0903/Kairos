"use client";

import Link from "next/link";
import { useState } from "react";
import type { OrderItemResult } from "@/features/orders/api";
import { ReviewFormDialog } from "@/features/reviews/components/ReviewFormDialog";
import { reviewAction } from "@/features/reviews/review-state";
import { formatDateTimeMinute } from "@/lib/datetime";

/** 주문 항목 줄의 상태 문구(칩 아래). 구매자 주문 상세에서만 그린다. */
export function OrderItemReviewNote({ item }: { item: Pick<OrderItemResult, "review"> }) {
  const action = reviewAction(item.review);
  if (action.kind === "none") return null;
  return (
    <span className="text-xs text-text-2">
      {action.kind === "write" && (
        <>
          <b className="font-medium tabular-nums text-foreground">{formatDateTimeMinute(action.until)}</b>까지 작성 가능
        </>
      )}
      {action.kind === "view" && "후기를 작성했습니다"}
      {action.kind === "text" && action.text}
    </span>
  );
}

/** 주문 항목 줄 오른쪽 버튼: 작성 가능이면 "후기 작성"(창), 작성했으면 "내 후기 보기"(/my/reviews). 그 밖에는 버튼이 없다. */
export function OrderItemReviewAction({ orderId, item }: { orderId: number; item: OrderItemResult }) {
  const [open, setOpen] = useState(false);
  const action = reviewAction(item.review);
  if (action.kind === "view") {
    return (
      <Link href="/my/reviews" className="btn btn-ghost btn-sm whitespace-nowrap">
        내 후기 보기
      </Link>
    );
  }
  if (action.kind !== "write") return null;
  return (
    <>
      <button type="button" className="btn btn-primary btn-sm whitespace-nowrap" onClick={() => setOpen(true)}>
        후기 작성
      </button>
      {open && (
        <ReviewFormDialog
          mode="create"
          orderId={orderId}
          orderItemId={item.orderItemId}
          productId={item.productId}
          product={{ name: item.productName, optionDisplayName: item.optionDisplayName, thumbnailUrl: item.thumbnailUrl }}
          onSubmitted={() => setOpen(false)}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}
