import type { ReactNode } from "react";
import { Store } from "lucide-react";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import { sellerOrderChip } from "@/features/orders/actions";
import { carrierName } from "@/features/orders/carriers";
import type { OrderItemResult, SellerOrderResult } from "@/features/orders/api";
import { OrderItemRow } from "@/features/orders/components/OrderItemRow";

/**
 * 판매자 주문 카드(구매자·판매자·관리자 화면이 함께 쓴다). 표시 전용이다.
 * - 모든 항목이 반품된 판매자 주문의 칩은 어느 화면에서나 "전체 반품"이다. 반품 진입·상태 줄은 구매자 화면이 renderItemAction·renderItemNote 로 넣는다.
 * - renderItemAction: 항목 오른쪽 버튼(취소 등). shipAction: 택배 줄 오른쪽 버튼(구매 확정 등). footer: 배송비 줄 아래 슬롯.
 */
export function SellerOrderCard({
  sellerOrder,
  renderItemAction,
  renderItemNote,
  shipAction,
  footer,
}: {
  sellerOrder: SellerOrderResult;
  renderItemAction?: (item: OrderItemResult) => ReactNode;
  /** 항목 상태 칩 아래 보조 문구(구매자 주문 상세의 후기 상태 등). */
  renderItemNote?: (item: OrderItemResult) => ReactNode;
  shipAction?: ReactNode;
  footer?: ReactNode;
}) {
  const carrier = carrierName(sellerOrder.carrierCode);
  const tracking = sellerOrder.trackingNumber;
  const showShip = Boolean(carrier || tracking || shipAction);
  return (
    <section aria-label={`${sellerOrder.sellerName} 주문`} className="mt-4 rounded-lg border border-border first:mt-0">
      <div className="flex min-h-12 items-center justify-between gap-2 rounded-t-lg border-b border-border bg-surface px-5">
        <span className="flex items-center gap-2 text-sm font-bold">
          <Store className="h-4 w-4" aria-hidden />
          {sellerOrder.sellerName}
        </span>
        <StatusChip kind="sellerOrder" status={sellerOrderChip(sellerOrder)} />
      </div>
      {showShip && (
        <div className="flex items-center justify-between gap-4 border-b border-border px-5 py-3 text-[13px] text-text-2">
          <div className="flex gap-5">
            {carrier && (
              <span>
                택배사<b className="ml-1.5 font-medium text-foreground">{carrier}</b>
              </span>
            )}
            {tracking && (
              <span>
                송장번호<b className="ml-1.5 font-medium tabular-nums text-foreground">{tracking}</b>
              </span>
            )}
          </div>
          {shipAction}
        </div>
      )}
      {sellerOrder.items.map((item) => (
        <OrderItemRow key={item.orderItemId} item={item} action={renderItemAction?.(item)} note={renderItemNote?.(item)} />
      ))}
      <div className="flex items-center justify-end gap-5 border-t border-border px-5 py-3.5 text-[13px] text-text-2">
        <span>
          배송비
          <b className="ml-1.5 text-foreground">
            <Money value={sellerOrder.shippingFee} />
          </b>
        </span>
        <span aria-hidden className="h-3 w-px bg-border" />
        <span>{sellerOrder.shippingFeePayer === "PLATFORM" ? "무료배송 · KAIROS 부담" : "구매자 부담"}</span>
      </div>
      {footer}
    </section>
  );
}
