"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { withdrawReturn } from "@/features/returns/api";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

/**
 * 반품 철회 버튼과 확인 창(주문 상세의 항목 줄과 반품 상세가 함께 쓴다).
 * 성공이든 409(그사이 승인)든 끝나면 주문 상세·반품 전체(상세·목록)를 서버에서 다시 읽는다. 진행 중에는 다시 누를 수 없다.
 */
export function WithdrawReturnControl({ orderId, returnId, className = "btn btn-ghost btn-sm" }: { orderId: number; returnId: number; className?: string }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);

  const run = async () => {
    setOpen(false);
    if (inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    try {
      await withdrawReturn(returnId);
      toast({ title: "반품 요청을 철회했습니다" });
    } catch (e) {
      toast({ title: e instanceof ApiError ? e.message : resolveError(e).message, variant: "destructive" });
    } finally {
      void qc.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) });
      void qc.invalidateQueries({ queryKey: queryKeys.returns.root });
      inFlight.current = false;
      setPending(false);
    }
  };

  return (
    <>
      <button type="button" className={className} disabled={pending} onClick={() => setOpen(true)}>
        {pending ? "철회 중…" : "철회"}
      </button>
      <ConfirmDialog
        open={open}
        title="반품 철회"
        description="반품 요청을 철회할까요? 철회하면 판매자에게 전달된 요청이 취소되며, 기한 안이라면 다시 반품을 요청할 수 있습니다."
        confirmLabel="반품 철회"
        destructive
        onConfirm={() => void run()}
        onCancel={() => setOpen(false)}
      />
    </>
  );
}
