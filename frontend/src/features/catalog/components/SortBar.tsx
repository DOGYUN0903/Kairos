import type { ProductSort } from "@/features/catalog/search-params";
import { cn } from "@/lib/utils";

// 시안은 4개지만 계획·API 는 LIKES 를 포함한 5개다. 순서: 인기·좋아요·최신·가격.
export const SORT_LABELS: { value: ProductSort; label: string }[] = [
  { value: "POPULAR", label: "인기순" },
  { value: "LIKES", label: "좋아요순" },
  { value: "LATEST", label: "최신순" },
  { value: "PRICE_ASC", label: "낮은 가격순" },
  { value: "PRICE_DESC", label: "높은 가격순" },
];

export function SortBar({ sort, onChange }: { sort: ProductSort; onChange: (sort: ProductSort) => void }) {
  return (
    <div role="group" aria-label="정렬" className="flex gap-0.5">
      {SORT_LABELS.map(({ value, label }, i) => (
        <button
          key={value}
          type="button"
          aria-pressed={sort === value}
          onClick={() => onChange(value)}
          className={cn(
            "min-h-[44px] px-3 font-medium text-text-2 aria-pressed:font-bold aria-pressed:text-navy",
            i > 0 && "relative before:mr-3.5 before:inline-block before:h-3 before:w-px before:bg-border before:align-[-1px] before:content-['']",
          )}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
