"use client";

import { resolveError } from "@/api/errors";
import { Money } from "@/components/common/Money";
import { refundLines } from "@/features/orders/actions";
import { useCancelItem, useCancelPreview, type OrderItemResult } from "@/features/orders/api";
import { ItemCancelDialog } from "@/features/orders/components/ItemCancelDialog";

/** 구매자 항목 취소 창. item 이 있을 때만 열린다(닫으면 내용이 언마운트되어 사유·미리보기가 새로 시작한다). */
export function CancelItemDialog({ orderId, item, onClose }: { orderId: number; item: OrderItemResult | null; onClose: () => void }) {
  if (!item) return null;
  return <CancelBody key={item.orderItemId} orderId={orderId} item={item} onClose={onClose} />;
}

function CancelBody({ orderId, item, onClose }: { orderId: number; item: OrderItemResult; onClose: () => void }) {
  const preview = useCancelPreview(orderId, item.orderItemId, true);
  const cancel = useCancelItem(orderId);
  return (
    <ItemCancelDialog
      item={item}
      cancel={(reason, key) => cancel(item.orderItemId, reason, key)}
      notice={<RefundPreview preview={preview} />}
      // 환불 예정 금액을 확인한 뒤에만 취소할 수 있다(미리보기가 409 면 취소할 수 없는 항목이다).
      canSubmit={preview.isSuccess}
      onClose={onClose}
    />
  );
}

/** 환불 미리보기. 금액은 모두 서버 값이다. */
function RefundPreview({ preview }: { preview: ReturnType<typeof useCancelPreview> }) {
  if (preview.isError) {
    return (
      <div role="alert" className="notice notice-warn">
        <span>{resolveError(preview.error).message}</span>
      </div>
    );
  }
  if (!preview.isSuccess) {
    return (
      <p className="text-[13px] text-text-2" aria-busy="true">
        환불 예정 금액을 확인하고 있습니다…
      </p>
    );
  }
  return (
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
      {preview.data.shippingFeeDeducted > 0 && <p className="-mt-2 text-[13px] text-text-2">무료배송 기준 금액 아래로 내려가 배송비가 차감됩니다.</p>}
    </>
  );
}
