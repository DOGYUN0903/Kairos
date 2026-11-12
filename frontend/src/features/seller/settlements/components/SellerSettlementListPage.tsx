"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { Pagination } from "@/components/common/Pagination";
import { StatusChip } from "@/components/common/StatusChip";
import { Skeleton } from "@/components/ui/skeleton";
import { useSellerSettlements } from "@/features/seller/settlements/api";
import { periodLastDay } from "@/lib/datetime";
import { formatKrwMinus } from "@/lib/money";
import { parsePage } from "@/lib/page";

const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";
const TD = "border-b border-border px-3.5 py-3 align-middle";

export function SellerSettlementListPage() {
  const router = useRouter();
  const page = parsePage(useSearchParams().get("page"));
  const q = useSellerSettlements(page);
  const go = (p: number) => router.push(p > 0 ? `/seller/settlements?page=${p}` : "/seller/settlements", { scroll: false });
  const data = q.data;
  const rows = data?.content ?? [];

  return (
    <div className="flex flex-col gap-4">
      <p className="text-[13px] text-text-2">정산은 매주 월요일에 지난주(월~일) 구매 확정 주문으로 만들어집니다. 최신 기간 순입니다.</p>
      <div className="card">
        {q.isError && !data ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : !data ? (
          <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 p-5">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          page > 0 && data.totalPages > 0 ? (
            <EmptyState message="해당 페이지에 정산이 없습니다." action={{ label: "첫 페이지로", onClick: () => go(0) }} />
          ) : (
            <EmptyState message="아직 정산이 없습니다. 정산은 매주 월요일에 지난주 구매 확정 주문으로 만들어집니다" />
          )
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr>
                    <th scope="col" className={TH}>정산 기간</th>
                    <th scope="col" className={TH}>지급 예정일</th>
                    <th scope="col" className={TH}>상태</th>
                    <th scope="col" className={`${TH} text-right`}>포함 주문</th>
                    <th scope="col" className={`${TH} text-right`}>합계</th>
                    <th scope="col" className={`${TH} text-right`}>지급액</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((s) => (
                    <tr key={s.settlementId}>
                      <td className={`${TD} whitespace-nowrap tabular-nums`}>
                        <Link href={`/seller/settlements/${s.settlementId}`} className="font-medium underline">
                          {s.periodStart} ~ {periodLastDay(s.periodEnd)}
                        </Link>
                      </td>
                      <td className={`${TD} whitespace-nowrap tabular-nums text-text-2`}>{s.payoutDate}</td>
                      <td className={TD}>
                        <StatusChip kind="settlement" status={s.status} />
                      </td>
                      <td className={`${TD} text-right tabular-nums`}>{s.lineCount}건</td>
                      <td className={`${TD} text-right tabular-nums`}>{formatKrwMinus(s.totalAmount)}</td>
                      <td className={`${TD} text-right font-bold tabular-nums`}>{formatKrwMinus(s.payableAmount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between px-3.5 pb-4 pt-3">
              <span className="text-xs text-text-2">총 {data.totalElements}건</span>
              <Pagination page={data.page} totalPages={data.totalPages} onChange={go} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
