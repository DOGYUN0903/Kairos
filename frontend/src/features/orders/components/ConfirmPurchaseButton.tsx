"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { useConfirmPurchase, useInvalidateOrders } from "@/features/orders/api";
import { ToastAction } from "@/components/ui/toast";
import { toast } from "@/hooks/use-toast";
import { queryKeys } from "@/lib/query-keys";

/** 구매 확정 버튼. blockedReason 이 있으면(열린 반품) 비활성으로 두고 이유를 보인다. 확인 창 뒤 PATCH 한 번, 처리 중에는 잠그고 두 번째 호출은 무시한다(서버는 키 없이 멱등). */
const OPEN_RETURN_MESSAGE = "반품이 진행 중인 상품이 있어 확정할 수 없습니다. 반품을 철회하거나 완료된 뒤 다시 시도해 주세요";

export function ConfirmPurchaseButton({ orderId, sellerOrderId, blockedReason = null }: { orderId: number; sellerOrderId: number; blockedReason?: string | null }) {
  const confirm = useConfirmPurchase(orderId);
  const invalidate = useInvalidateOrders(orderId);
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
      await confirm(sellerOrderId);
      void qc.invalidateQueries({ queryKey: queryKeys.points.root });
      toast({
        title: "구매가 확정되었습니다.",
        action: (
          <ToastAction altText="포인트 내역 보기" asChild>
            <Link href="/my/points">포인트 내역 보기</Link>
          </ToastAction>
        ),
      });
    } catch (e) {
      const message = e instanceof ApiError && e.code === "SELLER_ORDER_HAS_OPEN_RETURN" ? OPEN_RETURN_MESSAGE : resolveError(e).message;
      toast({ title: message, variant: "destructive" });
      void invalidate();
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  };

  return (
    <>
      <span className="flex items-center gap-3">
        {blockedReason && <span className="text-xs text-text-2">{blockedReason}</span>}
        <button type="button" className="btn btn-primary btn-sm" disabled={pending || blockedReason != null} onClick={() => setOpen(true)}>
          {pending ? "확정 중…" : "구매 확정"}
        </button>
      </span>
      <ConfirmDialog
        open={open}
        title="구매를 확정할까요?"
        description="확정하면 되돌릴 수 없습니다. 구매 확정 시 결제 금액의 1%가 포인트로 적립됩니다. 확정하면 이 판매자의 상품은 반품할 수 없습니다."
        confirmLabel="구매 확정"
        onConfirm={() => void run()}
        onCancel={() => setOpen(false)}
      />
    </>
  );
}
