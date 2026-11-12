import type { components } from "@/api/schema";
import { adjustmentTypeLabel } from "@/features/settlements/settlement-text";
import { formatSignedKrw } from "@/lib/money";

type Adjustment = components["schemas"]["SettlementAdjustmentResult"];

/** 정산에 붙은 조정(판매자·관리자 공용). 금액은 서버 값의 부호 그대로다. */
export function SettlementAdjustmentsTable({ adjustments }: { adjustments: Adjustment[] }) {
  return (
    <section className="card" aria-label="조정">
      <div className="border-b border-border px-5 py-4 text-[15px] font-bold">조정</div>
      {adjustments.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-text-2">조정이 없습니다</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th scope="col" className="bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2">종류</th>
                <th scope="col" className="bg-surface px-3.5 py-3 text-right text-xs font-medium text-text-2">금액</th>
                <th scope="col" className="bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2">사유</th>
              </tr>
            </thead>
            <tbody>
              {adjustments.map((a) => (
                <tr key={a.adjustmentId}>
                  <td className="border-b border-border px-3.5 py-3">{adjustmentTypeLabel(a.type)}</td>
                  <td className="border-b border-border px-3.5 py-3 text-right tabular-nums">{formatSignedKrw(a.amount)}</td>
                  <td className="border-b border-border px-3.5 py-3">{a.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
