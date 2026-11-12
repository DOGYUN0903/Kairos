"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Info } from "lucide-react";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useAdminCancelItem } from "@/features/admin/orders/api";
import type { OrderItemResult } from "@/features/orders/api";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { formatKrw } from "@/lib/money";

const REASON_MAX = 500;
const schema = z.object({ reason: z.string().trim().min(1, "취소 사유를 입력해 주세요.").max(REASON_MAX, `취소 사유는 ${REASON_MAX}자 이하로 입력해 주세요.`) });
type Values = z.infer<typeof schema>;

const REJECTED = "취소가 거절되었습니다. 주문은 그대로입니다";

/** 관리자 항목 취소 창. item 이 있을 때만 열린다(닫으면 내용이 언마운트되어 사유가 새로 시작한다). */
export function AdminCancelDialog({ orderId, item, onClose }: { orderId: number; item: OrderItemResult | null; onClose: () => void }) {
  if (!item) return null;
  return <CancelBody key={item.orderItemId} orderId={orderId} item={item} onClose={onClose} />;
}

function CancelBody({ orderId, item, onClose }: { orderId: number; item: OrderItemResult; onClose: () => void }) {
  const cancel = useAdminCancelItem(orderId);
  const [rejected, setRejected] = useState<string | null>(null);
  // 취소가 접수된 뒤(창이 닫히기 전)에는 같은 항목으로 다시 보내지 않는다.
  const [accepted, setAccepted] = useState(false);
  const acceptedRef = useRef(false);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { reason: "" } });
  const { register, formState, watch } = form;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      if (acceptedRef.current) return;
      setRejected(null);
      const res = await cancel(item.orderItemId, values.reason, key);
      if (res.itemStatus === "CANCELED" || res.itemStatus === "CANCEL_REQUESTED") {
        acceptedRef.current = true;
        setAccepted(true);
      }
      if (res.itemStatus === "CANCELED") {
        // 환불액은 서버가 확정한 값 그대로 알린다.
        toast({ title: `취소되었습니다. 환불 ${formatKrw(res.refundAmount)}` });
        onClose();
      } else if (res.itemStatus === "CANCEL_REQUESTED") {
        toast({ title: "취소를 확인하고 있습니다" });
        onClose();
      } else {
        setRejected(REJECTED);
      }
    },
    {
      onError: async (e) => {
        if (e instanceof ApiError && e.code === "PAYMENT_CANCEL_FAILED") {
          setRejected(REJECTED);
          return;
        }
        if (e instanceof ApiError && e.code === "ORDER_ITEM_NOT_CANCELABLE") {
          toast({ title: e.message, variant: "destructive" });
          onClose();
          return;
        }
        toast({ title: resolveError(e).message, variant: "destructive" });
      },
    },
  );

  const length = watch("reason").length;
  const reasonError = formState.errors.reason?.message;

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-[460px]">
        <DialogHeader>
          <DialogTitle>항목 취소</DialogTitle>
          <DialogDescription className="sr-only">취소할 항목과 사유를 확인합니다.</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-3">
          <OrderThumb url={item.thumbnailUrl} size={56} />
          <div className="min-w-0">
            <div className="text-xs text-text-2">{item.brandName}</div>
            <div className="truncate font-medium">{item.productName}</div>
            <div className="text-[13px] text-text-2">
              {item.optionDisplayName} · 수량 {item.quantity}개
            </div>
          </div>
        </div>
        <div className="notice">
          <Info className="h-4 w-4 flex-none text-navy" aria-hidden />
          <span>
            <strong>배송비 차감 없이 환불됩니다.</strong> 취소하면 되돌릴 수 없습니다.
          </span>
        </div>
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <div className="field">
            <label htmlFor="acancel-reason">
              취소 사유<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
              <span className="sr-only"> (필수)</span>
            </label>
            <textarea
              id="acancel-reason"
              rows={3}
              maxLength={REASON_MAX}
              required
              disabled={submitting}
              placeholder="취소 사유를 입력해 주세요"
              className="input min-h-[88px] py-2"
              aria-invalid={reasonError ? true : undefined}
              aria-describedby={reasonError ? "acancel-reason-err acancel-reason-count" : "acancel-reason-count"}
              {...register("reason")}
            />
            <div className="flex justify-between">
              <span id="acancel-reason-err" className="field-error">
                {reasonError}
              </span>
              <span id="acancel-reason-count" className="text-xs tabular-nums text-text-3">
                {length} / {REASON_MAX}
              </span>
            </div>
          </div>
          {rejected && (
            <div role="alert" className="notice notice-warn">
              <span>{rejected}</span>
            </div>
          )}
          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={onClose}>
              닫기
            </button>
            <button type="submit" className="btn btn-danger" disabled={submitting || accepted}>
              {submitting ? "취소 중…" : "항목 취소"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
