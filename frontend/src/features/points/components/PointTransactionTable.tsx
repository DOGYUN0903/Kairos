import Link from "next/link";
import type { PointTransaction } from "@/features/points/api";
import { signedPoints, transactionLabel, transactionOrderHref } from "@/features/points/transaction-text";
import { formatDateTime } from "@/lib/datetime";
import { formatPoints } from "@/lib/money";
import { cn } from "@/lib/utils";

/** 포인트 내역 표. 관리자 회원 화면도 쓴다(showOrderLink=false 면 내 주문 링크를 두지 않는다). */
export function PointTransactionTable({ rows, showOrderLink }: { rows: PointTransaction[]; showOrderLink: boolean }) {
  return (
    <div className="mt-4 overflow-x-auto rounded border border-border">
      <table className="w-full text-sm">
        <thead className="bg-surface text-left text-[13px] text-text-2">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">일시</th>
            <th scope="col" className="px-4 py-3 font-medium">내용</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">변동</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">잔액</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((t) => {
            const href = showOrderLink ? transactionOrderHref(t) : null;
            return (
              <tr key={t.transactionId} className="border-t border-border">
                <td className="whitespace-nowrap px-4 py-3 text-text-2">{formatDateTime(t.createdAt)}</td>
                <td className="px-4 py-3">
                  <span className="inline-flex flex-wrap items-center gap-2">
                    {transactionLabel(t.type)}
                    {href && (
                      <Link href={href} className="text-[13px] text-navy underline">
                        주문 보기
                      </Link>
                    )}
                  </span>
                </td>
                <td className={cn("whitespace-nowrap px-4 py-3 text-right font-bold", t.amount > 0 ? "text-success" : t.amount < 0 ? "text-danger" : "text-text-2")}>
                  {signedPoints(t.amount)}
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-right">{formatPoints(t.balanceAfter)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
