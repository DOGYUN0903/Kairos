import { isCarriedOver, settlementRows } from "@/features/settlements/settlement-text";
import type { components } from "@/api/schema";

type Detail = components["schemas"]["SettlementDetail"];

/** 정산 계산 내역(판매자·관리자 공용). 8줄은 0원이어도 고정이고 값은 서버 필드 그대로다. 합계가 음수면 이월 안내를 보인다. */
export function SettlementBreakdown({ detail }: { detail: Detail }) {
  const rows = settlementRows(detail);
  const total = rows[6]!;
  const payable = rows[7]!;
  return (
    <section className="card" aria-label="계산 내역">
      <div className="border-b border-border px-5 py-4 text-[15px] font-bold">계산 내역</div>
      <div className="flex flex-col gap-3 p-5">
        {isCarriedOver(detail) && (
          <div className="notice notice-warn" role="status">
            <span>합계가 0원보다 적어 이번 지급액은 0원입니다. 부족한 금액은 다음 정산으로 넘어갑니다</span>
          </div>
        )}
        <ul className="flex flex-col gap-2 text-sm">
          {rows.slice(0, 6).map((r) => (
            <li key={r.label} className="flex justify-between gap-3">
              <span className="text-text-2">
                <span className="inline-block w-5" aria-hidden={r.sign ? undefined : true}>{r.sign}</span>
                {r.label}
              </span>
              <span className="tabular-nums">{r.text}</span>
            </li>
          ))}
          <li className="flex justify-between gap-3 border-t border-border pt-3 font-bold">
            <span>
              <span className="inline-block w-5">{total.sign}</span>
              {total.label}
            </span>
            <span className="tabular-nums">{total.text}</span>
          </li>
          <li className="flex items-baseline justify-between gap-3 font-bold">
            <span>{payable.label}</span>
            <strong className="text-xl tracking-tight tabular-nums">{payable.text}</strong>
          </li>
        </ul>
      </div>
    </section>
  );
}
