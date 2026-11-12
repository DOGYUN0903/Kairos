import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type Tone = "default" | "wait" | "muted";

/** 주문 상세 시안의 상태 헤더(status-hero)와 같은 모양. 결제 결과 화면들이 함께 쓴다. */
export function StatusPanel({
  title,
  tone = "default",
  busy = false,
  heading: Heading = "h1",
  side,
  children,
}: {
  title: string;
  tone?: Tone;
  busy?: boolean;
  heading?: "h1" | "h2";
  side?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div
      className={cn(
        "flex items-center justify-between gap-6 rounded-lg border border-border bg-surface px-7 py-6",
        tone === "wait" && "border-[#F0DFA8] bg-[#FFFBEF]",
      )}
    >
      <div className="min-w-0">
        <Heading className={cn("flex items-center gap-2.5 text-[22px] font-bold tracking-tight", tone === "muted" && "text-text-2")}>
          {busy && (
            <span
              aria-hidden
              className="h-[22px] w-[22px] flex-none animate-spin rounded-full border-[2.5px] border-border border-t-navy motion-reduce:animate-none"
            />
          )}
          {title}
        </Heading>
        {children}
      </div>
      {side && <div className="flex flex-none items-center gap-5">{side}</div>}
    </div>
  );
}

export function PanelDesc({ children }: { children: ReactNode }) {
  return <p className="mt-1 text-text-2">{children}</p>;
}

export function PanelMeta({ items }: { items: { label: string; value: ReactNode }[] }) {
  return (
    <div className="mt-3.5 flex flex-wrap gap-5 text-[13px] text-text-2">
      {items.map((it) => (
        <span key={it.label}>
          {it.label}
          <b className="ml-1.5 font-medium tabular-nums text-foreground">{it.value}</b>
        </span>
      ))}
    </div>
  );
}

export function PanelActions({ children }: { children: ReactNode }) {
  return <div className="mt-5 flex justify-end gap-2">{children}</div>;
}
