import Link from "next/link";
import { Money } from "@/components/common/Money";
import { StatusChip } from "@/components/common/StatusChip";
import type { OrderSummary } from "@/features/orders/api";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import { formatDateTime } from "@/lib/datetime";
import { cn } from "@/lib/utils";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

export function AdminOrderTable({ rows }: { rows: OrderSummary[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th scope="col" className={TH}>주문번호</th>
            <th scope="col" className={TH}>상품</th>
            <th scope="col" className={`${TH} text-right`}>결제 금액</th>
            <th scope="col" className={TH}>상태</th>
            <th scope="col" className={TH}>주문 일시</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((o) => (
            <tr key={o.orderId}>
              <td className={TD}>
                <Link href={`/admin/orders/${o.orderId}`} className="font-medium tabular-nums hover:underline">
                  {o.orderNo}
                </Link>
              </td>
              <td className={TD}>
                <div className={cn("flex items-center gap-3", o.status === "CANCELED" && "opacity-55")}>
                  <OrderThumb url={o.thumbnailUrl} size={44} />
                  <div className="min-w-0">
                    <span className="block truncate font-medium">{o.firstProductName}</span>
                    {o.itemCount > 1 && <span className="text-xs text-text-2">외 {o.itemCount - 1}건</span>}
                  </div>
                </div>
              </td>
              <td className={`${TD} text-right font-bold`}>
                <Money value={o.totalAmount} />
              </td>
              <td className={TD}>
                <StatusChip kind="order" status={o.status} />
              </td>
              <td className={`${TD} tabular-nums text-text-2`}>{formatDateTime(o.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
