"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useRef, useState, type FormEvent } from "react";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { isAnswered } from "@/api/idempotency";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { OrderItemResult } from "@/features/orders/api";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { previewBlocksRequest, requestReturn, useReturnPreview } from "@/features/returns/api";
import { ReturnPreviewBox } from "@/features/returns/components/ReturnPreviewBox";
import { returnReasonLabel } from "@/features/returns/return-text";
import { RETURN_DETAIL_MAX, RETURN_REASONS, returnRequestSchema, toReturnBody, type ReturnRequestValues } from "@/features/returns/schemas";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { joinDot } from "@/lib/join-dot";
import { queryKeys } from "@/lib/query-keys";

/** 구매자 반품 요청 창. item 이 있을 때만 열린다(닫으면 언마운트되어 사유·미리보기가 새로 시작한다). */
export function ReturnRequestDialog({ orderId, item, onClose }: { orderId: number; item: OrderItemResult | null; onClose: () => void }) {
  if (!item) return null;
  return <ReturnRequestBody key={item.orderItemId} orderId={orderId} item={item} onClose={onClose} />;
}

function ReturnRequestBody({ orderId, item, onClose }: { orderId: number; item: OrderItemResult; onClose: () => void }) {
  const qc = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  // 접수된 뒤(창이 닫히기 전)에는 다시 보내지 않는다. state 반영 전의 연타도 막도록 ref 로도 잠근다.
  const accepted = useRef(false);
  const form = useForm<ReturnRequestValues>({ resolver: zodResolver(returnRequestSchema), defaultValues: { detail: "" } });
  const { register, formState, watch } = form;
  const reason = watch("reason") ?? null;
  const detailLength = watch("detail").length;
  const preview = useReturnPreview(orderId, item.orderItemId, reason);
  const blocked = reason != null && preview.isError && previewBlocksRequest(preview.error);

  const refreshOrder = () => void qc.invalidateQueries({ queryKey: queryKeys.orders.detail(orderId) });

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      if (accepted.current) return;
      setFormError(null);
      await requestReturn(orderId, item.orderItemId, toReturnBody(values), key);
      accepted.current = true;
      toast({ title: "반품을 요청했습니다. 판매자가 3일 안에 확인합니다" });
      refreshOrder();
      void qc.invalidateQueries({ queryKey: queryKeys.returns.root });
      onClose();
    },
    {
      onError: (e) => {
        if (e instanceof ApiError && e.status === 409 && isAnswered(e)) {
          // 그사이 상태가 바뀌었다: 서버 메시지를 알리고 주문 상세를 다시 읽는다.
          toast({ title: e.message, variant: "destructive" });
          refreshOrder();
          void qc.invalidateQueries({ queryKey: queryKeys.returns.root });
          onClose();
          return;
        }
        if (e instanceof ApiError && e.status === 400 && (e.code === "RETURN_INVALID" || e.code === "INVALID_REQUEST")) {
          setFormError(e.message);
          return;
        }
        toast({ title: resolveError(e).message, variant: "destructive" });
      },
    },
  );

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    // Enter 로도 제출되므로 버튼 비활성만으로는 부족하다.
    if (blocked || accepted.current) {
      e.preventDefault();
      return;
    }
    void onSubmit(e);
  };

  const reasonError = formState.errors.reason?.message;
  const detailError = formState.errors.detail?.message;

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-[520px] grid-cols-[minmax(0,1fr)]">
        <DialogHeader>
          <DialogTitle>반품 요청</DialogTitle>
          <DialogDescription className="sr-only">반품할 상품과 사유를 확인하고 환불 예정 금액을 봅니다.</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-3">
          <OrderThumb url={item.thumbnailUrl} size={56} />
          <div className="min-w-0">
            {item.brandName && <div className="text-xs text-text-2">{item.brandName}</div>}
            <div className="truncate font-medium">{item.productName}</div>
            <div className="text-[13px] text-text-2">{joinDot(item.optionDisplayName, `수량 ${item.quantity}개`)}</div>
          </div>
        </div>

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          {formError && (
            <div role="alert" className="notice notice-error">
              <span>{formError}</span>
            </div>
          )}
          <div className="field">
            <span className="label" id="return-reason-label">
              반품 사유<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
            </span>
            <div role="radiogroup" aria-labelledby="return-reason-label" className="flex flex-col gap-1.5">
              {RETURN_REASONS.map((r) => (
                <label key={r} className="flex cursor-pointer items-center gap-2 text-sm">
                  <input type="radio" value={r} disabled={submitting} {...register("reason")} />
                  <b className="font-medium">{returnReasonLabel(r)}</b>
                </label>
              ))}
            </div>
            <span className="field-error">{reasonError}</span>
          </div>

          <div className="field">
            <label htmlFor="return-detail">
              상세 내용 <span className="font-normal text-text-2">(선택)</span>
            </label>
            <textarea
              id="return-detail"
              rows={3}
              disabled={submitting}
              placeholder="반품 사유를 자세히 적어 주세요"
              className="input min-h-[76px] py-2"
              aria-invalid={detailError ? true : undefined}
              {...register("detail")}
            />
            <div className="flex justify-between">
              <span className="field-error">{detailError}</span>
              <span className="text-xs tabular-nums text-text-3">
                {detailLength} / {RETURN_DETAIL_MAX}
              </span>
            </div>
          </div>

          <ReturnPreviewBox reason={reason} preview={preview} />

          <div className="flex justify-end gap-2">
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={onClose}>
              취소
            </button>
            <button type="submit" className="btn btn-primary" disabled={submitting || blocked}>
              {submitting ? "요청 중…" : "반품 요청"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
