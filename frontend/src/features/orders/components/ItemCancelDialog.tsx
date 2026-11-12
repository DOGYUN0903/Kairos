"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { joinDot } from "@/lib/join-dot";
import { useRef, useState, type FormEvent, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { ApiError } from "@/api/client";
import { resolveError } from "@/api/errors";
import { isAnswered } from "@/api/idempotency";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { CancelItemResponse, OrderItemResult } from "@/features/orders/api";
import { CANCEL_REASON_MAX, cancelReasonSchema, type CancelReasonValues } from "@/features/orders/cancel-schema";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { useIdempotentSubmit } from "@/hooks/useIdempotentSubmit";
import { toast } from "@/hooks/use-toast";
import { formatKrw } from "@/lib/money";

const REJECTED = "취소가 거절되었습니다. 주문은 그대로입니다";
const UNKNOWN_RESULT = "취소 결과를 확인하지 못했습니다. 주문 상태를 확인해 주세요";

/** 창에 보여줄 항목 정보. brandName 이 없으면 브랜드 줄을 그리지 않는다(판매자 화면). */
export type CancelDialogItem = Pick<OrderItemResult, "orderItemId" | "productName" | "optionDisplayName" | "quantity" | "thumbnailUrl"> & {
  brandName?: string | null;
};

type Props = {
  /** 표시용. 어떤 항목을 취소할지는 cancel 이 정한다. */
  item: CancelDialogItem;
  /** 취소 요청. 실패하면 던진다. 주문 상세·목록 무효화는 각 api 훅이 한다(이 창은 하지 않는다). */
  cancel: (reason: string, key: string) => Promise<CancelItemResponse>;
  /** 환불 안내 영역(구매자: 환불 미리보기 / 판매자·관리자: 배송비 차감 없음 안내). */
  notice: ReactNode;
  /** false 면 제출을 막는다(구매자: 미리보기를 확인하지 못했을 때). */
  canSubmit?: boolean;
  /** 화면 고유의 오류 처리. true 를 돌려주면 공통 처리를 건너뛴다. */
  onSpecialError?: (e: unknown) => boolean;
  /** 제출 버튼 모양. 문구는 같다. */
  submitTone?: "primary" | "danger";
  onClose: () => void;
};

/**
 * 주문 항목 취소 창(구매자·판매자·관리자 공용). 마운트되어 있는 동안 열려 있다(닫으면 언마운트되어 사유가 새로 시작한다).
 * 결과 처리:
 * - CANCELED → 서버가 확정한 환불액(차감된 배송비가 있으면 함께)을 알리고 닫는다
 * - CANCEL_REQUESTED → 확인 중이라고 알리고 닫는다(성공으로 말하지 않는다)
 * - 응답 ORDERED·409 PAYMENT_CANCEL_FAILED → 거절 안내, 창 유지
 * - 409 ORDER_ITEM_NOT_CANCELABLE → 서버 메시지를 알리고 닫는다
 * - 응답을 받지 못함 → 결과를 모른다고 알리고 창 유지(같은 키로 다시 보낼 수 있다)
 */
export function ItemCancelDialog({ item, cancel, notice, canSubmit = true, onSpecialError, submitTone = "primary", onClose }: Props) {
  const [rejected, setRejected] = useState<string | null>(null);
  // 취소가 접수된 뒤(창이 닫히기 전)에는 같은 항목으로 다시 보내지 않는다. state 반영 전의 연타도 막도록 ref 로도 잠근다.
  const [accepted, setAccepted] = useState(false);
  const acceptedRef = useRef(false);
  const form = useForm<CancelReasonValues>({ resolver: zodResolver(cancelReasonSchema), defaultValues: { reason: "" } });
  const { register, formState, watch } = form;

  const { onSubmit, submitting } = useIdempotentSubmit(
    form,
    async (values, key) => {
      if (acceptedRef.current) return;
      setRejected(null);
      const res = await cancel(values.reason, key);
      if (res.itemStatus === "CANCELED" || res.itemStatus === "CANCEL_REQUESTED") {
        acceptedRef.current = true;
        setAccepted(true);
      }
      if (res.itemStatus === "CANCELED") {
        // 환불액·차감액은 서버가 확정한 값 그대로 알린다.
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
      onError: (e) => {
        if (onSpecialError?.(e)) return;
        // 응답을 받지 못했다: 취소됐는지 모른다. 성공도 실패도 말하지 않는다.
        if (!isAnswered(e)) {
          toast({ title: UNKNOWN_RESULT, variant: "destructive" });
          return;
        }
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
  const blocked = !canSubmit || accepted;

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    // Enter 로도 제출되므로 버튼 비활성만으로는 부족하다.
    if (!canSubmit || acceptedRef.current) {
      e.preventDefault();
      return;
    }
    void onSubmit(e);
  };

  return (
    <Dialog open onOpenChange={(open) => !open && !submitting && onClose()}>
      <DialogContent className="max-w-[460px]">
        <DialogHeader>
          <DialogTitle>주문 항목 취소</DialogTitle>
          <DialogDescription className="sr-only">취소할 항목과 환불 안내를 확인하고 사유를 입력합니다.</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-3">
          <OrderThumb url={item.thumbnailUrl} size={56} />
          <div className="min-w-0">
            {item.brandName && <div className="text-xs text-text-2">{item.brandName}</div>}
            <div className="truncate font-medium">{item.productName}</div>
            <div className="text-[13px] text-text-2">
              {joinDot(item.optionDisplayName, `수량 ${item.quantity}개`)}
            </div>
          </div>
        </div>

        {notice}

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <div className="field">
            <label htmlFor="cancel-reason">
              취소 사유<span aria-hidden="true" className="ml-0.5 text-danger">*</span>
              <span className="sr-only"> (필수)</span>
            </label>
            <textarea
              id="cancel-reason"
              rows={3}
              maxLength={CANCEL_REASON_MAX}
              required
              disabled={submitting}
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
                {length} / {CANCEL_REASON_MAX}
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
            <button type="submit" className={submitTone === "danger" ? "btn btn-danger" : "btn btn-primary"} disabled={submitting || blocked}>
              {submitting ? "취소 중…" : "취소하기"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
