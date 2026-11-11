import Link from "next/link";
import type { SellerDetail } from "@/features/seller/apply/api";
import { formatBusinessNumber } from "@/features/seller/apply/schemas";

export function ApplicationSummary({ seller }: { seller: SellerDetail }) {
  const rows: [string, string][] = [
    ["회사명", seller.companyName],
    ["사업자등록번호", formatBusinessNumber(seller.businessNumber)],
    ["대표자", seller.representativeName],
    ["연락처", seller.phone],
    ["정산 계좌", `${seller.bank} ${seller.accountNumber} · 예금주 ${seller.holder}`],
  ];
  return (
    <section className="card" aria-labelledby="application-h">
      <div className="card__head flex items-center justify-between gap-3 border-b border-border px-5 py-4">
        <h2 id="application-h" className="text-[15px] font-bold">
          입점 신청 내용
        </h2>
        <Link href="/seller/apply" className="btn btn-ghost btn-sm">
          수정
        </Link>
      </div>
      <div className="p-5">
        <table className="w-full border-collapse">
          <tbody>
            {rows.map(([k, v]) => (
              <tr key={k}>
                <th scope="row" className="w-40 border-b border-border py-3 text-left text-[13px] font-medium text-text-2">
                  {k}
                </th>
                <td className="border-b border-border py-3 text-sm">{v}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
