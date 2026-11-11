"use client";

import { REVIEW_TABS, TAB_LABEL, type ReviewTab } from "@/features/admin/review/review-params";
import { cn } from "@/lib/utils";

const num = new Intl.NumberFormat("ko-KR");

/** 4개 큐 탭. 화살표 키로 이동, 각 탭에 대기 건수. */
export function QueueTabs({ tab, counts, onChange }: { tab: ReviewTab; counts: Record<ReviewTab, number | undefined>; onChange: (t: ReviewTab) => void }) {
  const onKeyDown = (e: React.KeyboardEvent, i: number) => {
    const delta = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0;
    if (delta === 0) return;
    e.preventDefault();
    const last = REVIEW_TABS.length - 1;
    const target = i + delta;
    const next = REVIEW_TABS[target < 0 ? last : target > last ? 0 : target];
    if (next) {
      onChange(next);
      document.getElementById(`review-tab-${next}`)?.focus();
    }
  };
  return (
    <div role="tablist" aria-label="검수 대상" className="flex gap-1 border-b border-border">
      {REVIEW_TABS.map((t, i) => {
        const selected = t === tab;
        const count = counts[t];
        return (
          <button
            key={t}
            id={`review-tab-${t}`}
            type="button"
            role="tab"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(t)}
            onKeyDown={(e) => onKeyDown(e, i)}
            className={cn(
              "-mb-px inline-flex min-h-[44px] items-center gap-1.5 border-b-2 border-transparent px-4 font-medium text-text-2",
              selected && "border-navy font-bold text-navy",
            )}
          >
            {TAB_LABEL[t]}
            <span
              aria-label={count === undefined ? undefined : `대기 ${count}건`}
              className={cn("inline-grid h-5 min-w-5 place-items-center rounded-full bg-surface-2 px-1.5 text-[11px] font-bold text-text-2", selected && "bg-navy text-white")}
            >
              {count === undefined ? "–" : num.format(count)}
            </span>
          </button>
        );
      })}
    </div>
  );
}
