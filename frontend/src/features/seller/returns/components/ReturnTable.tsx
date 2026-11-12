import Link from "next/link";
import { StatusChip } from "@/components/common/StatusChip";
import { OrderThumb } from "@/features/orders/components/OrderThumb";
import type { ReturnRequestResponse } from "@/features/returns/api";
import { feeBearerLabel, returnReasonLabel } from "@/features/returns/return-text";
import { formatDate, formatMonthDay } from "@/lib/datetime";
import { joinDot } from "@/lib/join-dot";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

/**
 * 반품 목록 표(판매자·관리자 공용). 링크는 detailHref 로 받고, 관리자는 showSeller 로 판매자 열을 더한다.
 * REQUESTED 줄에는 서버가 준 자동 승인 예정일을 보인다.
 */
export function ReturnTable({ rows, detailHref, showSeller = false }: { rows: ReturnRequestResponse[]; detailHref: (returnId: number) => string; showSeller?: boolean }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th scope="col" className={TH}>요청일</th>
            <th scope="col" className={TH}>주문번호</th>
            {showSeller && <th scope="col" className={TH}>판매자</th>}
            <th scope="col" className={TH}>상품</th>
            <th scope="col" className={TH}>사유</th>
            <th scope="col" className={TH}>부담 주체</th>
            <th scope="col" className={TH}>상태</th>
            <th scope="col" className={TH}>자동 승인</th>
            <th scope="col" className={`${TH} text-right`}>작업</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.returnId}>
              <td className={`${TD} whitespace-nowrap tabular-nums text-text-2`}>{formatDate(r.requestedAt)}</td>
              <td className={TD}>
                <Link href={detailHref(r.returnId)} className="font-medium tabular-nums hover:underline">
                  {r.orderNo}
                </Link>
              </td>
              {showSeller && <td className={TD}>{r.sellerName}</td>}
              <td className={TD}>
                <div className="flex items-center gap-3">
                  <OrderThumb url={r.thumbnailUrl} size={44} />
                  <div className="min-w-0">
                    <span className="block truncate font-medium">{r.productName}</span>
                    <span className="text-xs text-text-2">{joinDot(r.optionDisplayName, `${r.quantity}개`)}</span>
                  </div>
                </div>
              </td>
              <td className={`${TD} whitespace-nowrap`}>{returnReasonLabel(r.reason)}</td>
              <td className={`${TD} whitespace-nowrap`}>{feeBearerLabel(r.feeBearer)}</td>
              <td className={TD}>
                <StatusChip kind="return" status={r.status} />
              </td>
              <td className={`${TD} whitespace-nowrap text-[13px] text-text-2`}>{r.status === "REQUESTED" && r.autoApproveAt ? `${formatMonthDay(r.autoApproveAt)} 자동 승인` : ""}</td>
              <td className={`${TD} text-right`}>
                <Link href={detailHref(r.returnId)} className="btn btn-sm" aria-label={`${r.orderNo} 반품 보기`}>
                  보기
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
