"use client";

import { resolveError } from "@/api/errors";
import { Money } from "@/components/common/Money";
import { benefitNotices } from "@/features/orders/benefit-notices";
import { refundLines } from "@/features/orders/actions";
import { useCancelItem, useCancelPreview, type OrderItemResult } from "@/features/orders/api";
import { ItemCancelDialog } from "@/features/orders/components/ItemCancelDialog";

/** 구매자 항목 취소 창. item 이 있을 때만 열린다(닫으면 내용이 언마운트되어 사유·미리보기가 새로 시작한다). */
export function CancelItemDialog({ orderId, item, hasCartCoupon = false, onClose }: { orderId: number; item: OrderItemResult | null; hasCartCoupon?: boolean; onClose: () => void }) {
  if (!item) return null;
  return <CancelBody key={item.orderItemId} orderId={orderId} item={item} hasCartCoupon={hasCartCoupon} onClose={onClose} />;
}

function CancelBody({ orderId, item, hasCartCoupon, onClose }: { orderId: number; item: OrderItemResult; hasCartCoupon: boolean; onClose: () => void }) {
  const preview = useCancelPreview(orderId, item.orderItemId, true);
  const cancel = useCancelItem(orderId);
  const notices = benefitNotices({ pointsAllocated: item.pointsAllocated, hasProductCoupon: item.productCouponId != null, hasCartCoupon });
  return (
    <ItemCancelDialog
      item={item}
      cancel={(reason, key) => cancel(item.orderItemId, reason, key)}
      notice={
        <>
          <RefundPreview preview={preview} />
          {notices.length > 0 && (
            <ul className="notice flex-col gap-1" aria-label="쿠폰·포인트 안내">
              {notices.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
          )}
        </>
      }
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
        {/* 라벨과 값을 dt/dd 로 묶어 둔다(보조 기술과 E2E 가 값을 라벨로 찾는다). */}
        <dl className="mt-3 flex items-baseline justify-between border-t border-border pt-3 font-bold">
          <dt>환불 예정 금액</dt>
          <dd>
            <strong className="text-xl tracking-tight">
              <Money value={preview.data.refundAmount} />
            </strong>
          </dd>
        </dl>
      </div>
      {preview.data.shippingFeeDeducted > 0 && <p className="-mt-2 text-[13px] text-text-2">무료배송 기준 금액 아래로 내려가 배송비가 차감됩니다.</p>}
    </>
  );
}
