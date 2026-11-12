import Link from "next/link";
import { StatusChip } from "@/components/common/StatusChip";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { refundSummaryText, returnReasonLabel } from "@/features/returns/return-text";
import { formatDate } from "@/lib/datetime";
import { joinDot } from "@/lib/join-dot";

/** 반품 내역 카드. 전체가 반품 상세 링크다. */
export function ReturnCard({ r }: { r: ReturnRequestResponse }) {
  return (
    <Link href={`/my/returns/${r.returnId}`} className="grid grid-cols-[72px_minmax(0,1fr)_auto_auto] items-center gap-5 rounded-lg border border-border p-4 hover:bg-surface">
      <OrderThumb url={r.thumbnailUrl} size={72} />
      <div className="min-w-0">
        <div className="text-xs text-text-2">{r.sellerName}</div>
        <div className="mt-1 truncate text-[15px] font-medium">{r.productName}</div>
        <div className="mt-0.5 text-[13px] text-text-2">{joinDot(r.optionDisplayName, `수량 ${r.quantity}개`)}</div>
      </div>
      <div className="flex flex-col gap-1 text-[13px] text-text-2">
        <span>
          <b className="mr-1.5 font-medium text-foreground">사유</b>
          {returnReasonLabel(r.reason)}
        </span>
        <span>
          <b className="mr-1.5 font-medium text-foreground">요청일</b>
          <span className="tabular-nums">{formatDate(r.requestedAt)}</span>
        </span>
      </div>
      <div className="flex min-w-[120px] flex-col items-end gap-1.5">
        <StatusChip kind="return" status={r.status} />
        <span className="text-xs text-text-2">{refundSummaryText(r)}</span>
      </div>
    </Link>
  );
}
