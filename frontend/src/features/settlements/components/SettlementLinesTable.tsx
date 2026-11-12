import Link from "next/link";
import type { components } from "@/api/schema";
import { formatDate } from "@/lib/datetime";
import { formatBp, formatKrwMinus, formatSignedKrw } from "@/lib/money";

type Line = components["schemas"]["SettlementLineResult"];

const TH = "whitespace-nowrap bg-surface px-3 py-2.5 text-right text-xs font-medium text-text-2";
const TD = "whitespace-nowrap border-b border-border px-3 py-2.5 text-right tabular-nums";

/** 정산에 포함된 주문 줄(판매자·관리자 공용). 주문 링크는 orderHref 로 받고 없으면 글자만 보인다. 수수료율은 줄마다 다를 수 있다. */
export function SettlementLinesTable({ lines, orderHref }: { lines: Line[]; orderHref?: (line: Line) => string | undefined }) {
  return (
    <section className="card" aria-label="포함된 주문">
      <div className="border-b border-border px-5 py-4 text-[15px] font-bold">
        포함된 주문 <span className="ml-1.5 text-xs font-normal text-text-2">구매 확정 주문 {lines.length}건</span>
      </div>
      {lines.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-text-2">포함된 주문이 없습니다</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-[13px]">
            <thead>
              <tr>
                <th scope="col" className={`${TH} text-left`}>주문번호</th>
                <th scope="col" className={TH}>확정일</th>
                <th scope="col" className={TH}>매출</th>
                <th scope="col" className={TH}>판매자 쿠폰</th>
                <th scope="col" className={TH}>수수료 기준액</th>
                <th scope="col" className={TH}>수수료율</th>
                <th scope="col" className={TH}>수수료</th>
                <th scope="col" className={TH}>배송비</th>
                <th scope="col" className={TH}>반품비</th>
                <th scope="col" className={TH}>라인 금액</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => {
                const href = orderHref?.(l);
                return (
                  <tr key={l.lineId}>
                    <td className={`${TD} text-left`}>
                      {href ? (
                        <Link href={href} className="font-medium underline">
                          {l.orderNo}
                        </Link>
                      ) : (
                        l.orderNo
                      )}
                    </td>
                    <td className={`${TD} text-text-2`}>{formatDate(l.confirmedAt)}</td>
                    <td className={TD}>{formatKrwMinus(l.salesAmount)}</td>
                    <td className={TD}>{formatSignedKrw(-l.sellerCouponDiscount)}</td>
                    <td className={TD}>{formatKrwMinus(l.commissionBase)}</td>
                    <td className={TD}>{formatBp(l.commissionRateBp)}</td>
                    <td className={TD}>{formatSignedKrw(-l.commissionAmount)}</td>
                    <td className={TD}>{formatSignedKrw(l.shippingFeeOwed)}</td>
                    <td className={TD}>{formatSignedKrw(-l.returnFeeBorne)}</td>
                    <td className={`${TD} font-bold`}>{formatKrwMinus(l.lineAmount)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
