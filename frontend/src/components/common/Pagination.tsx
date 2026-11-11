import { cn } from "@/lib/utils";

const WINDOW = 5;

/** page 는 0부터(백엔드 Page 와 같음), 화면에는 1부터 보인다. */
export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  const start = Math.max(0, Math.min(page - 2, totalPages - WINDOW));
  const pages = Array.from({ length: Math.min(WINDOW, totalPages) }, (_, i) => start + i);
  const base = "inline-grid h-10 min-w-10 place-items-center rounded border px-2 text-sm";
  return (
    <nav aria-label="페이지" className="mt-6 flex justify-center gap-1">
      <button type="button" className={cn(base, "border-border disabled:text-text-3")} disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="이전 페이지">
        ‹
      </button>
      {pages.map((p) => (
        <button
          key={p}
          type="button"
          aria-current={p === page ? "page" : undefined}
          className={cn(base, p === page ? "border-navy bg-navy font-bold text-white" : "border-border hover:bg-surface")}
          onClick={() => onChange(p)}
        >
          {p + 1}
        </button>
      ))}
      <button
        type="button"
        className={cn(base, "border-border disabled:text-text-3")}
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        aria-label="다음 페이지"
      >
        ›
      </button>
    </nav>
  );
}
