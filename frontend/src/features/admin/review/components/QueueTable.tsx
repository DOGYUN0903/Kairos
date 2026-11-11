"use client";

import { StatusChip } from "@/components/common/StatusChip";
import { CHANGED_FIELD_LABEL, type QueueRow } from "@/features/admin/review/rows";
import type { ReviewTab } from "@/features/admin/review/review-params";
import { formatDateTime } from "@/lib/datetime";
import { cn } from "@/lib/utils";

const CHIP_KIND = { sellers: "seller", brands: "brand", products: "product", "change-requests": "changeRequest" } as const;
const TH = "whitespace-nowrap bg-surface px-3.5 py-3 text-left text-xs font-medium text-text-2";

/** 판매자 탭의 이름이 곧 회사명이고 브랜드 행은 판매자명이 없어서, 두 탭은 판매자 열을 두지 않는다. */
export function showsSellerColumn(tab: ReviewTab): boolean {
  return tab === "products" || tab === "change-requests";
}

export function QueueTable({ tab, rows, selectedId, onSelect }: { tab: ReviewTab; rows: QueueRow[]; selectedId: number | null; onSelect: (id: number) => void }) {
  const seller = showsSellerColumn(tab);
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr>
            <th scope="col" className={TH}>이름</th>
            {seller && <th scope="col" className={TH}>판매자</th>}
            {tab === "change-requests" && <th scope="col" className={TH}>변경 항목</th>}
            <th scope="col" className={TH}>요청일</th>
            <th scope="col" className={TH}>상태</th>
            <th scope="col" className={TH}><span className="sr-only">열기</span></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const selected = r.id === selectedId;
            const td = cn("border-b border-border px-3.5 py-3 align-middle", selected && "bg-[#E8EDF7]");
            return (
              <tr key={r.id} aria-current={selected ? "true" : undefined} className={cn(!selected && "hover:bg-[#FAFBFC]")}>
                <td className={td}>
                  <span className="font-medium">{r.name}</span>
                  {r.meta && <span className="block text-xs text-text-3">{r.meta}</span>}
                </td>
                {seller && <td className={td}>{r.sellerName}</td>}
                {r.tab === "change-requests" && (
                  <td className={td}>
                    {r.raw.changedFields.length === 0 ? (
                      <span className="text-text-3">-</span>
                    ) : (
                      <span className="flex flex-wrap gap-1">
                        {r.raw.changedFields.map((f) => (
                          <span key={f} className="rounded-sm bg-surface-2 px-1.5 py-px text-[11px] font-medium text-text-2">{CHANGED_FIELD_LABEL[f]}</span>
                        ))}
                      </span>
                    )}
                  </td>
                )}
                <td className={cn(td, "whitespace-nowrap text-text-2")}>{formatDateTime(r.at)}</td>
                <td className={td}><StatusChip kind={CHIP_KIND[r.tab]} status={r.status} /></td>
                <td className={td}>
                  <button type="button" className="btn btn-ghost btn-sm" aria-pressed={selected} aria-label={`${r.name} ${selected ? "보는 중" : "열기"}`} onClick={() => onSelect(r.id)}>
                    {selected ? "보는 중" : "열기"}
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
