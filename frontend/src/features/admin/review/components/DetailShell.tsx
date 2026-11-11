import type { ReactNode } from "react";
import { StatusChip } from "@/components/common/StatusChip";
import { DecisionBar } from "@/features/admin/review/components/DecisionBar";
import type { QueueRow } from "@/features/admin/review/rows";

const CHIP_KIND = { sellers: "seller", brands: "brand", products: "product", "change-requests": "changeRequest" } as const;

/** 우측 상세 카드의 뼈대: 제목·부제·상태 칩, 본문, 승인/반려 바. */
export function DetailShell({
  row,
  title,
  sub,
  ready,
  onDone,
  children,
}: {
  row: QueueRow;
  title: string;
  sub?: ReactNode;
  ready?: boolean;
  onDone: (id: number) => void;
  children: ReactNode;
}) {
  return (
    <section className="card overflow-hidden" aria-labelledby="detail-h">
      <div className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
        <div className="min-w-0">
          <h2 id="detail-h" className="text-[15px] font-bold">{title}</h2>
          {sub && <div className="mt-0.5 text-xs text-text-2">{sub}</div>}
        </div>
        <StatusChip kind={CHIP_KIND[row.tab]} status={row.status} />
      </div>
      <div className="flex flex-col gap-4 px-5 py-4 text-sm">{children}</div>
      <DecisionBar row={row} onDone={onDone} {...(ready === undefined ? {} : { ready })} />
    </section>
  );
}

export function Facts({ items }: { items: [label: string, value: ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[96px_1fr] gap-x-4 gap-y-2">
      {items.map(([k, v]) => (
        <div key={k} className="contents">
          <dt className="text-text-2">{k}</dt>
          <dd className="min-w-0 break-words">{v}</dd>
        </div>
      ))}
    </dl>
  );
}
