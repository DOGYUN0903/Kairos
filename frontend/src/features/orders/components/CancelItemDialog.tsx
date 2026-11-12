"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { Money } from "@/components/common/Money";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { refundLines } from "@/features/orders/actions";
import { useCancelItem, useCancelPreview, useInvalidateOrders, type OrderItemResult } from "@/features/orders/api";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { formatKrw } from "@/lib/money";

const REASON_MAX = 500;
const schema = z.object({ reason: z.string().trim().min(1, "취소 사유를 입력해 주세요.").max(REASON_MAX, `취소 사유는 ${REASON_MAX}자 이하로 입력해 주세요.`) });
type Values = z.infer<typeof schema>;

const REJECTED = "취소가 거절되었습니다. 주문은 그대로입니다";

/** 항목 취소 창. item 이 있을 때만 열린다(닫으면 내용이 언마운트되어 사유·미리보기가 새로 시작한다). */
export function CancelItemDialog({ orderId, item, onClose }: { orderId: number; item: OrderItemResult | null; onClose: () => void }) {
  if (!item) return null;
  return <CancelBody key={item.orderItemId} orderId={orderId} item={item} onClose={onClose} />;
}

function CancelBody({ orderId, item, onClose }: { orderId: number; item: OrderItemResult; onClose: () => void }) {
  const preview = useCancelPreview(orderId, item.orderItemId, true);
  const cancel = useCancelItem(orderId);
  const invalidate = useInvalidateOrders(orderId);
  const [rejected, setRejected] = useState<string | null>(null);
  // 취소가 접수된 뒤(창이 닫히기 전)에 같은 항목으로 다시 보내지 않는다.
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
        const deducted = res.shippingFeeDeducted > 0 ? ` (배송비 ${formatKrw(res.shippingFeeDeducted)} 차감)` : "";
        toast({ title: `취소되었습니다. 환불 ${formatKrw(res.refundAmount)}${deducted}` });
        onClose();
      } else if (res.itemStatus === "CANCEL_REQUESTED") {
        toast({ title: "취소를 확인하고 있습니다" });
        onClose();
      } else {
        // 서버가 취소하지 않고 ORDERED 를 돌려준 경우: 성공으로 보지 않는다.
        setRejected(REJECTED);
      }
    },
    {
      onError: async (e) => {
        void invalidate();
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
  const previewError = preview.isError ? resolveError(preview.error).message : null;
  const canSubmit = preview.isSuccess && !submitting && !accepted;
  const reasonError = formState.errors.reason?.message;

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-[460px]">
        <DialogHeader>
          <DialogTitle>주문 항목 취소</DialogTitle>
          <DialogDescription className="sr-only">취소할 항목의 환불 예정 금액을 확인하고 사유를 입력합니다.</DialogDescription>
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

        {previewError ? (
          <div role="alert" className="notice notice-warn">
            <span>{previewError}</span>
          </div>
        ) : preview.isSuccess ? (
          <>
            <div className="rounded-lg border border-border bg-surface p-4" aria-live="polite">
              <div className="mb-2.5 text-[13px] font-bold">환불 예정 내역</div>
              <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-2 text-sm">
                {refundLines(preview.data).map((l) => (
                  <div key={l.label} className="col-span-2 flex justify-between">
                    <dt className="text-text-2">{l.label}</dt>
                    <dd className={l.negative ? "tabular-nums text-danger" : "tabular-nums"}>
                      {l.negative && "−"}
                      <Money value={l.amount} />
                    </dd>
                  </div>
                ))}
              </dl>
              <div className="mt-3 flex items-baseline justify-between border-t border-border pt-3 font-bold">
                <span>환불 예정 금액</span>
                <strong className="text-xl tracking-tight">
                  <Money value={preview.data.refundAmount} />
                </strong>
              </div>
            </div>
            {preview.data.shippingFeeDeducted > 0 && (
              <p className="-mt-2 text-[13px] text-text-2">무료배송 기준 금액 아래로 내려가 배송비가 차감됩니다.</p>
            )}
          </>
        ) : (
          <p className="text-[13px] text-text-2" aria-busy="true">
            환불 예정 금액을 확인하고 있습니다…
          </p>
        )}

        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <div className="field">
            <label htmlFor="cancel-reason">
              취소 사유<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
              <span className="sr-only"> (필수)</span>
            </label>
            <textarea
              id="cancel-reason"
              rows={3}
              maxLength={REASON_MAX}
              required
              disabled={submitting || Boolean(previewError)}
              placeholder="취소 사유를 입력해 주세요"
              className="input min-h-[88px] py-2"
              aria-invalid={reasonError ? true : undefined}
              aria-describedby={reasonError ? "cancel-reason-err cancel-reason-count" : "cancel-reason-count"}
              {...register("reason")}
            />
            <div className="flex justify-between">
              <span id="cancel-reason-err" className="field-error">
                {reasonError}
              </span>
              <span id="cancel-reason-count" className="text-xs tabular-nums text-text-3">
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
            <button type="submit" className="btn btn-primary" disabled={!canSubmit}>
              {submitting ? "취소 중…" : "취소하기"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
