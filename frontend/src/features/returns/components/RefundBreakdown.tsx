import { Money } from "@/components/common/Money";
import type { refundRows } from "@/features/returns/return-actions";
import { formatKrw, formatPoints, formatSignedKrw } from "@/lib/money";

/**
 * 환불 내역 줄(서버 값). 판매자·관리자·반품 상세도 쓴다. 금액은 줄 목록(refundRows)이 정한 그대로 그린다.
 * title 이 "환불 예정 금액"이면 미리보기이므로 합계 줄에 "예상"을 붙인다.
 */
export function RefundBreakdown({ rows, title }: { rows: ReturnType<typeof refundRows>; title: "환불 예정 금액" | "환불 금액" }) {
  const estimate = title === "환불 예정 금액";
  const refund = rows.find((r) => r.key === "refund");
  const rest = rows.filter((r) => r.key !== "refund" && r.unit === "won");
  const points = rows.filter((r) => r.unit === "point");
  return (
    <div className="rounded-lg border border-border bg-surface p-4" aria-live="polite">
      <div className="mb-2.5 text-[13px] font-bold">{title}</div>
      <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-2 text-sm">
        {rest.map((r) => (
          <div key={r.key} className="col-span-2 flex justify-between">
            <dt className="text-text-2">{r.label}</dt>
            <dd className="tabular-nums">{r.sign === "minus" ? formatSignedKrw(-r.amount) : r.sign === "plus" ? formatSignedKrw(r.amount) : formatKrw(r.amount)}</dd>
          </div>
        ))}
      </dl>
      {refund && (
        <dl className="mt-3 flex items-baseline justify-between border-t border-border pt-3 font-bold">
          <dt>
            {refund.label}
            {estimate && <em className="ml-1.5 text-xs font-medium not-italic text-text-2">예상</em>}
          </dt>
          <dd>
            <strong className="text-xl tracking-tight">
              <Money value={refund.amount} />
            </strong>
          </dd>
        </dl>
      )}
      {points.length > 0 && (
        <dl className="mt-2 flex flex-col gap-1 text-sm">
          {points.map((r) => (
            <div key={r.key} className="flex justify-between">
              <dt className="text-text-2">{r.label}</dt>
              <dd className="tabular-nums">{formatPoints(r.amount)}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}
