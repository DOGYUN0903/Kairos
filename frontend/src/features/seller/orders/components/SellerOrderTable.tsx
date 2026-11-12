import Link from "next/link";
import { Money } from "@/components/common/Money";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import type { SellerOrderSummary } from "@/features/seller/orders/api";
import { SellerStatusChip } from "@/features/seller/orders/components/SellerStatusChip";
import { formatDateTime } from "@/lib/datetime";
import { cn } from "@/lib/utils";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

export function SellerOrderTable({ rows }: { rows: SellerOrderSummary[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th scope="col" className={TH}>주문번호</th>
            <th scope="col" className={TH}>상품</th>
            <th scope="col" className={`${TH} text-right`}>상품 금액</th>
            <th scope="col" className={TH}>상태</th>
            <th scope="col" className={TH}>주문 일시</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((o) => {
            const canceled = o.status === "CANCELED";
            return (
              <tr key={o.sellerOrderId}>
                <td className={TD}>
                  <Link href={`/seller/orders/${o.sellerOrderId}`} className="font-medium tabular-nums hover:underline">
                    {o.orderNo}
                  </Link>
                </td>
                <td className={TD}>
                  <div className={cn("flex items-center gap-3", canceled && "opacity-55")}>
                    <OrderThumb url={o.thumbnailUrl} size={44} />
                    <div className="min-w-0">
                      <span className="block truncate font-medium">{o.firstProductName}</span>
                      {o.itemCount > 1 && <span className="text-xs text-text-2">외 {o.itemCount - 1}건</span>}
                    </div>
                  </div>
                </td>
                <td className={`${TD} text-right font-bold`}>
                  <Money value={o.itemsAmount} />
                </td>
                <td className={TD}>
                  <SellerStatusChip status={o.status} />
                </td>
                <td className={`${TD} tabular-nums text-text-2`}>{formatDateTime(o.orderedAt)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
