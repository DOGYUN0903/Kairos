"use client";

import { useRef, useState } from "react";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useConfirmPurchase, useInvalidateOrders } from "@/features/orders/api";
import { toast } from "@/hooks/use-toast";

/** 구매 확정 버튼. 확인 창 뒤 PATCH 한 번, 처리 중에는 잠그고 두 번째 호출은 무시한다(서버는 키 없이 멱등). */
export function ConfirmPurchaseButton({ orderId, sellerOrderId }: { orderId: number; sellerOrderId: number }) {
  const confirm = useConfirmPurchase(orderId);
  const invalidate = useInvalidateOrders(orderId);
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);

  const run = async () => {
    setOpen(false);
    if (inFlight.current) return;
    inFlight.current = true;
    setPending(true);
    try {
      await confirm(sellerOrderId);
      toast({ title: "구매가 확정되었습니다." });
    } catch (e) {
      toast({ title: resolveError(e).message, variant: "destructive" });
      void invalidate();
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  };

  return (
    <>
      <button type="button" className="btn btn-primary btn-sm" disabled={pending} onClick={() => setOpen(true)}>
        {pending ? "확정 중…" : "구매 확정"}
      </button>
      <ConfirmDialog
        open={open}
        title="구매를 확정할까요?"
        description="확정하면 되돌릴 수 없습니다."
        confirmLabel="구매 확정"
        onConfirm={() => void run()}
        onCancel={() => setOpen(false)}
      />
    </>
  );
}
