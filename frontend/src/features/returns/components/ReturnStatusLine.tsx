"use client";

import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { resolveError } from "@/api/errors";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { ApiError } from "@/api/client";
import type { OrderItemResult } from "@/features/orders/api";
import { withdrawReturn } from "@/features/returns/api";
import { canWithdraw } from "@/features/returns/return-actions";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

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
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const r = item.returnRequest;
  if (!r || r.status === "WITHDRAWN") return null;

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) });
    void qc.invalidateQueries({ queryKey: queryKeys.returns.root });
  };

  const run = async () => {
    setOpen(false);
    if (inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    try {
      await withdrawReturn(r.returnId);
      toast({ title: "반품 요청을 철회했습니다" });
    } catch (e) {
      toast({ title: e instanceof ApiError ? e.message : resolveError(e).message, variant: "destructive" });
    } finally {
      // 성공이든 409(그사이 승인)든 결과를 서버에서 다시 읽는다.
      refresh();
      inFlight.current = false;
      setPending(false);
    }
  };

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
        {canWithdraw(r.status) && (
          <button type="button" className="btn btn-ghost btn-sm" disabled={pending} onClick={() => setOpen(true)}>
            {pending ? "철회 중…" : "철회"}
          </button>
        )}
        <Link className="text-text-2 underline" href={`/my/returns/${r.returnId}`}>
          반품 상세
        </Link>
      </span>
      <ConfirmDialog
        open={open}
        title="반품 철회"
        description="반품 요청을 철회할까요? 철회하면 판매자에게 전달된 요청이 취소되며, 기한 안이라면 다시 반품을 요청할 수 있습니다."
        confirmLabel="반품 철회"
        destructive
        onConfirm={() => void run()}
        onCancel={() => setOpen(false)}
      />
    </div>
  );
}
