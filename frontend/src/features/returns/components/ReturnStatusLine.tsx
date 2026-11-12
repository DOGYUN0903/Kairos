"use client";

import Link from "next/link";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import type { OrderItemResult } from "@/features/orders/api";
import { WithdrawReturnControl } from "@/features/returns/components/WithdrawReturnControl";
import { canWithdraw } from "@/features/returns/return-actions";

const HINT: Partial<Record<string, string>> = {
  REQUESTED: "판매자가 3일 안에 확인합니다",
  APPROVED: "상품을 판매자에게 보내 주세요",
  REFUNDING: "환불을 확인하고 있습니다",
};

/**
 * 주문 항목의 반품 상태(구매자 주문 상세). 철회된 반품은 보이지 않는다.
 * 항목 상태 칩이 이미 반품 상태를 말하는 동안(RETURN_*·RETURNED)에는 칩을 다시 그리지 않고,
 * 항목이 ORDERED 로 돌아온 거절·중단 때만 반품 상태 칩을 보인다.
 */
export function ReturnStatusLine({ orderId, item }: { orderId: number; item: Pick<OrderItemResult, "status" | "returnRequest" | "productName"> }) {
  const r = item.returnRequest;
  if (!r || r.status === "WITHDRAWN") return null;

  return (
    <div className="flex flex-col items-start gap-1 text-xs">
      {item.status === "ORDERED" && <StatusChip kind="return" status={r.status} />}
      {HINT[r.status] && <span className="text-text-2">{HINT[r.status]}</span>}
      {r.status === "COMPLETED" && (
        <span>
          환불 <Money value={r.refundAmount} />
        </span>
      )}
      <span className="flex items-center gap-2">
        {canWithdraw(r.status) && <WithdrawReturnControl orderId={orderId} returnId={r.returnId} />}
        <Link className="text-text-2 underline" href={`/my/returns/${r.returnId}`}>
          반품 상세
        </Link>
      </span>
    </div>
  );
}
